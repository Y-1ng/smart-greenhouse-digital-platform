package com.smartgreenhouse.api.modules.chain;

import com.smartgreenhouse.api.integration.chain.ChainInvoker;
import com.smartgreenhouse.api.integration.chain.ChainBusinessKey;
import com.smartgreenhouse.api.integration.chain.ChainReceipt;
import com.smartgreenhouse.api.modules.trace.TraceNodeSyncReport;
import com.smartgreenhouse.api.modules.trace.TraceNodeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 链上存证补偿：把业务写入时因链不可用而保留为 PENDING 的记录重新提交上链。
 *
 * 补偿顺序固定为环境周期、合格证、溯源码、扫码记录、追溯节点：扫码记录依赖溯源码已在链上存在，
 * 顺序颠倒会导致合约报「trace code not found」。扫码记录的待补偿查询额外要求
 * 溯源码已经有链上回执，避免溯源码补链失败时反复提交注定回滚的交易。
 */
@Service
public class ChainCompensationService {

    /** 连续失败达到该次数即认为链上服务不可用，本轮补偿提前结束。 */
    private static final int MAX_CONSECUTIVE_FAILURES = 2;

    private static final Logger log =
            LoggerFactory.getLogger(ChainCompensationService.class);

    private final ChainCompensationMapper mapper;
    private final ChainInvoker chainInvoker;
    private final TraceNodeSyncService traceNodeSyncService;
    private final int batchSize;

    public ChainCompensationService(
            ChainCompensationMapper mapper,
            ChainInvoker chainInvoker,
            TraceNodeSyncService traceNodeSyncService,
            @Value("${app.chain.compensation.batch-size:20}") int batchSize
    ) {
        this.mapper = mapper;
        this.chainInvoker = chainInvoker;
        this.traceNodeSyncService = traceNodeSyncService;
        this.batchSize = batchSize;
    }

    @Transactional(readOnly = true)
    public ChainPendingView countPending() {
        return ChainPendingView.of(
                mapper.countPendingCycles(),
                mapper.countPendingCertificates(),
                mapper.countPendingTraceCodes(),
                mapper.countPendingTraceNodes(),
                mapper.countPendingScans()
        );
    }

    /** 全平台存证记录总数（五类存证表行数之和），供界面展示真实计数 */
    @Transactional(readOnly = true)
    public int countProofs() {
        return mapper.countProofs();
    }

    /**
     * 按批重试待补偿记录。单条失败只记录原因并保留 PENDING，不影响同批其它记录。
     */
    public ChainCompensationReport compensate() {
        List<ChainCompensationReport.Entry> entries = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int attempted = 0;
        int consecutiveFailures = 0;

        for (ChainCompensationMapper.PendingCycle cycle
                : mapper.findPendingCycles(batchSize)) {
            attempted++;
            ChainReceipt receipt = chainInvoker.recordEnvironmentRoot(
                    ChainBusinessKey.environmentCycle(
                            cycle.greenhouseCode(),
                            cycle.cycleStart(),
                            cycle.cycleEnd()
                    ),
                    cycle.rootHash(),
                    cycle.dataDigest()
            );
            if (receipt.transactionHash() == null) {
                failures.add("环境周期 " + cycle.cycleId() + " 补偿失败，保留 PENDING");
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    return abort(attempted, entries, failures);
                }
                continue;
            }
            consecutiveFailures = 0;
            mapper.confirmCycle(cycle.cycleId(), receipt.transactionHash());
            entries.add(entry(
                    "ENVIRONMENT_CYCLE",
                    String.valueOf(cycle.cycleId()),
                    receipt
            ));
        }

        for (ChainCompensationMapper.PendingCertificate certificate
                : mapper.findPendingCertificates(batchSize)) {
            attempted++;
            ChainReceipt receipt = chainInvoker.recordCertificate(
                    String.valueOf(certificate.certificateId()),
                    certificate.contentHash(),
                    certificate.status()
            );
            if (receipt.transactionHash() == null) {
                failures.add("合格证 " + certificate.certificateId()
                        + " 补偿失败，保留 PENDING");
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    return abort(attempted, entries, failures);
                }
                continue;
            }
            consecutiveFailures = 0;
            mapper.confirmCertificate(
                    certificate.certificateId(),
                    receipt.transactionHash()
            );
            entries.add(entry(
                    "CERTIFICATE",
                    String.valueOf(certificate.certificateId()),
                    receipt
            ));
        }

        for (ChainCompensationMapper.PendingTraceCode traceCode
                : mapper.findPendingTraceCodes(batchSize)) {
            attempted++;
            ChainReceipt receipt = chainInvoker.registerTraceCode(
                    traceCode.productCode(),
                    sha256(traceCode.signatureValue()),
                    String.valueOf(traceCode.batchId())
            );
            if (receipt.transactionHash() == null) {
                failures.add("溯源码 " + traceCode.productCode()
                        + " 补偿失败，保留 PENDING");
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    return abort(attempted, entries, failures);
                }
                continue;
            }
            consecutiveFailures = 0;
            mapper.confirmTraceCode(
                    traceCode.traceCodeId(),
                    receipt.transactionHash()
            );
            entries.add(entry(
                    "TRACE_CODE",
                    traceCode.productCode(),
                    receipt
            ));
        }

        for (ChainCompensationMapper.PendingScan scan
                : mapper.findPendingScans(batchSize)) {
            attempted++;
            ChainReceipt receipt = chainInvoker.appendScanRecord(
                    scan.productCode(),
                    scan.channel(),
                    scan.result()
            );
            if (receipt.transactionHash() == null) {
                failures.add("扫码记录 " + scan.scanId() + " 补偿失败，保留 PENDING");
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    return abort(attempted, entries, failures);
                }
                continue;
            }
            consecutiveFailures = 0;
            mapper.confirmScan(scan.scanId(), receipt.transactionHash());
            entries.add(entry(
                    "SCAN_RECORD",
                    String.valueOf(scan.scanId()),
                    receipt
            ));
        }

        TraceNodeSyncReport nodeReport =
                traceNodeSyncService.syncPending(batchSize);
        attempted += nodeReport.total();
        for (TraceNodeSyncReport.Confirmed node : nodeReport.confirmed()) {
            entries.add(new ChainCompensationReport.Entry(
                    "TRACE_NODE",
                    node.fieldNo() + "#" + node.nodeId(),
                    node.transactionHash(),
                    node.blockNumber()
            ));
        }
        if (nodeReport.failed() > 0) {
            failures.add("追溯节点补偿失败，保留 PENDING（链上链尾与本地指针不一致）");
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                return abort(attempted, entries, failures);
            }
        }

        if (attempted > 0) {
            log.info(
                    "链上补偿结束：尝试 {} 条，成功 {} 条，失败 {} 条",
                    attempted,
                    entries.size(),
                    failures.size()
            );
        }
        return new ChainCompensationReport(
                attempted,
                entries.size(),
                failures.size(),
                entries,
                failures
        );
    }

    /**
     * 链上服务不可用时提前结束本轮：剩余待补偿记录保留到下一轮，
     * 避免长时间占用调度线程拖慢遥测注入等其它定时任务。
     */
    private ChainCompensationReport abort(
            int attempted,
            List<ChainCompensationReport.Entry> entries,
            List<String> failures
    ) {
        log.warn(
                "链上服务连续失败 {} 次，本轮补偿提前结束：尝试 {} 条，成功 {} 条",
                MAX_CONSECUTIVE_FAILURES,
                attempted,
                entries.size()
        );
        return new ChainCompensationReport(
                attempted,
                entries.size(),
                failures.size(),
                entries,
                failures
        );
    }

    private ChainCompensationReport.Entry entry(
            String type,
            String businessId,
            ChainReceipt receipt
    ) {
        return new ChainCompensationReport.Entry(
                type,
                businessId,
                receipt.transactionHash(),
                receipt.blockNumber()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
