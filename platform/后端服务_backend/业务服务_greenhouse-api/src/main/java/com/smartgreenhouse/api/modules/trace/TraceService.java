package com.smartgreenhouse.api.modules.trace;

import com.smartgreenhouse.api.modules.environment.EnvironmentMetrics;
import com.smartgreenhouse.api.integration.chain.ChainInvoker;
import com.smartgreenhouse.api.integration.chain.ChainReceipt;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TraceService {

    private static final String GENESIS_HASH = "0".repeat(64);
    private static final int SYNC_BATCH = 50;

    private final TraceMapper mapper;
    private final ChainInvoker chainInvoker;
    private final TraceNodeSyncService nodeSyncService;

    public TraceService(
            TraceMapper mapper,
            ChainInvoker chainInvoker,
            TraceNodeSyncService nodeSyncService
    ) {
        this.mapper = mapper;
        this.chainInvoker = chainInvoker;
        this.nodeSyncService = nodeSyncService;
    }

    @Transactional(readOnly = true)
    public TraceView getTrace(String productCode) {
        TraceMapper.TraceRow trace = mapper.findByProductCode(productCode);
        if (trace == null) {
            return new TraceView(
                    productCode,
                    false,
                    "NOT_FOUND",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    List.of(),
                    "未找到对应的产品追溯记录"
            );
        }

        List<TraceMapper.TraceNodeRow> nodes =
                mapper.findTraceNodes(trace.fieldNo());
        boolean pointerChainValid = isPointerChainValid(nodes);
        boolean verified =
                "ACTIVE".equals(trace.traceStatus())
                        && "VALID".equals(trace.certificateStatus())
                        && pointerChainValid;

        return new TraceView(
                trace.productCode(),
                true,
                verified ? "VERIFIED" : "RISK",
                trace.batchId(),
                trace.certificateNo(),
                trace.certificateStatus(),
                trace.fieldNo(),
                trace.publicKeyId(),
                trace.rootHash(),
                environmentView(trace),
                pointerChainValid,
                nodes.stream()
                        .map(node -> new TraceView.TraceStageView(
                                node.nodeId(),
                                node.stageType(),
                                node.previousHash(),
                                node.payloadHash(),
                                node.chainTxHash(),
                                node.createdAt()
                        ))
                        .toList(),
                verified ? "签名、证书和追溯指针链均已核验" : "追溯记录存在风险"
        );
    }

    private TraceView.EnvironmentView environmentView(
            TraceMapper.TraceRow trace
    ) {
        if (trace.environmentCycleId() == null) {
            return null;
        }

        Map<String, TraceMapper.CycleMetricRow> latestByMetric =
                new LinkedHashMap<>();
        for (TraceMapper.CycleMetricRow row
                : mapper.findCycleMetrics(trace.environmentCycleId())) {
            latestByMetric.put(
                    EnvironmentMetrics.normalize(row.metricCode()),
                    row
            );
        }
        List<TraceView.EnvironmentMetricView> metrics = latestByMetric
                .values()
                .stream()
                .sorted(Comparator
                        .comparingInt((TraceMapper.CycleMetricRow row) ->
                                metricOrder(row.metricCode()))
                        .thenComparing(row ->
                                EnvironmentMetrics.normalize(row.metricCode())))
                .map(row -> new TraceView.EnvironmentMetricView(
                        EnvironmentMetrics.normalize(row.metricCode()),
                        row.metricValue(),
                        row.unit(),
                        row.collectedAt()
                ))
                .toList();
        LocalDateTime collectedAt = metrics.stream()
                .map(TraceView.EnvironmentMetricView::collectedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new TraceView.EnvironmentView(
                trace.environmentCycleId(),
                trace.environmentCycleStart(),
                trace.environmentCycleEnd(),
                trace.environmentChainStatus(),
                trace.dataDigest(),
                collectedAt,
                metrics
        );
    }

    /** 四项必填指标排在最前，其余指标按名称追加。 */
    private int metricOrder(String metricCode) {
        int index = EnvironmentMetrics.REQUIRED_CODES.indexOf(
                EnvironmentMetrics.normalize(metricCode)
        );
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    @Transactional
    public ScanResultView scan(
            String productCode,
            TraceController.ScanRequest request
    ) {
        TraceMapper.TraceRow trace = mapper.findByProductCode(productCode);
        if (trace == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "溯源码不存在");
        }

        int previousCount = mapper.countScans(trace.traceCodeId());
        String result = previousCount == 0 ? "FIRST_SCAN" : "REPEAT_SCAN";
        LocalDateTime scanTime = LocalDateTime.now();
        ChainReceipt receipt = chainInvoker.appendScanRecord(
                productCode,
                request.channel(),
                result
        );
        mapper.insertScan(new TraceMapper.ScanInsert(
                trace.traceCodeId(),
                scanTime,
                request.channel(),
                sha256(request.deviceFingerprint()),
                result,
                receipt.transactionHash()
        ));

        return new ScanResultView(
                productCode,
                result,
                previousCount == 0,
                previousCount + 1,
                request.channel(),
                scanTime,
                receipt.transactionHash()
        );
    }

    @Transactional(readOnly = true)
    public TracePathView getFieldPath(String fieldNo) {
        String productCode = mapper.findProductCodeByField(fieldNo);
        if (productCode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "田块追溯记录不存在");
        }
        List<TraceMapper.TraceNodeRow> nodes =
                mapper.findTraceNodes(fieldNo);
        return new TracePathView(
                fieldNo,
                productCode,
                isPointerChainValid(nodes),
                nodes.stream()
                        .map(node -> new TraceView.TraceStageView(
                                node.nodeId(),
                                node.stageType(),
                                node.previousHash(),
                                node.payloadHash(),
                                node.chainTxHash(),
                                node.createdAt()
                        ))
                        .toList()
        );
    }

    private boolean isPointerChainValid(
            List<TraceMapper.TraceNodeRow> nodes
    ) {
        if (nodes.isEmpty()) {
            return false;
        }
        String expectedPrevious =
                "0000000000000000000000000000000000000000000000000000000000000000";
        for (TraceMapper.TraceNodeRow node : nodes) {
            if (!expectedPrevious.equals(node.previousHash())) {
                return false;
            }
            expectedPrevious = node.payloadHash();
        }
        return true;
    }


    /**
     * 追加田块追溯节点：先补齐该田块历史待上链节点，再写入新节点并尝试上链。
     * 链上不可用时节点仍保存成功，链上状态由补偿任务补齐。
     */
    @Transactional
    public TraceNodeView appendNode(
            String fieldNo,
            TraceController.AppendNodeRequest request
    ) {
        if (mapper.countTraceCodesByField(fieldNo) == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "该田块尚未绑定产品追溯码"
            );
        }

        nodeSyncService.syncField(fieldNo, SYNC_BATCH);

        List<TraceMapper.TraceNodeRow> nodes = mapper.findTraceNodes(fieldNo);
        String previousHash = nodes.isEmpty()
                ? GENESIS_HASH
                : nodes.get(nodes.size() - 1).payloadHash();
        String payloadHash = resolvePayloadHash(fieldNo, previousHash, request);

        mapper.insertTraceNode(new TraceMapper.TraceNodeInsert(
                fieldNo,
                request.stageType().trim(),
                previousHash,
                payloadHash,
                LocalDateTime.now()
        ));
        nodeSyncService.syncField(fieldNo, SYNC_BATCH);

        TraceMapper.TraceNodeRow created =
                mapper.findNodeByPayloadHash(fieldNo, payloadHash);
        List<TraceMapper.TraceNodeRow> updated = mapper.findTraceNodes(fieldNo);
        String chainTxHash = created == null ? null : created.chainTxHash();

        return new TraceNodeView(
                fieldNo,
                created == null ? null : created.nodeId(),
                request.stageType().trim(),
                previousHash,
                payloadHash,
                chainTxHash,
                isPointerChainValid(updated),
                updated.size(),
                chainTxHash == null
                        ? "追溯节点已保存，链上状态待补偿"
                        : "追溯节点已写入并完成链上存证"
        );
    }

    private String resolvePayloadHash(
            String fieldNo,
            String previousHash,
            TraceController.AppendNodeRequest request
    ) {
        String provided = request.payloadHash();
        if (provided != null && !provided.isBlank()) {
            String normalized = provided.trim().toLowerCase();
            if (!normalized.matches("[0-9a-f]{64}")) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "payloadHash 必须是 64 位十六进制摘要"
                );
            }
            return normalized;
        }
        String summary = request.summary() == null
                ? ""
                : request.summary().trim();
        return sha256(String.join(
                "|",
                fieldNo,
                request.stageType().trim(),
                summary,
                previousHash
        ));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
