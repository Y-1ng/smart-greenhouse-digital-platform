package com.smartgreenhouse.api.modules.trace;

import com.smartgreenhouse.api.integration.chain.ChainInvoker;
import com.smartgreenhouse.api.integration.chain.ChainReceipt;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TraceCodeService {

    private final TraceMapper mapper;
    private final ChainInvoker chainInvoker;

    public TraceCodeService(TraceMapper mapper, ChainInvoker chainInvoker) {
        this.mapper = mapper;
        this.chainInvoker = chainInvoker;
    }

    @Transactional
    public TraceCodeView create(TraceCodeController.CreateRequest request) {
        if (mapper.countBatch(request.batchId()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作物批次不存在");
        }
        if (mapper.findByProductCode(request.productCode()) != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "产品编码已存在"
            );
        }
        mapper.insertTraceCode(new TraceMapper.TraceCodeInsert(
                request.batchId(),
                request.fieldNo(),
                request.productCode(),
                request.publicKeyId(),
                request.signatureValue()
        ));

        ChainReceipt receipt = chainInvoker.registerTraceCode(
                request.productCode(),
                sha256(request.signatureValue()),
                String.valueOf(request.batchId())
        );
        if (receipt.transactionHash() != null) {
            mapper.updateChainTxHash(
                    request.productCode(),
                    receipt.transactionHash()
            );
        }

        return new TraceCodeView(
                request.productCode(),
                request.fieldNo(),
                request.batchId(),
                request.publicKeyId(),
                "ACTIVE",
                receipt.transactionHash()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "摘要算法不可用"
            );
        }
    }
}
