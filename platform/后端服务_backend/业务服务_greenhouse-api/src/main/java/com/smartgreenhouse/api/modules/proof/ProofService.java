package com.smartgreenhouse.api.modules.proof;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProofService {

    private final ProofMapper mapper;

    public ProofService(ProofMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProofView getProof(Long batchId) {
        ProofMapper.BatchRow batch = mapper.findBatch(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作物批次不存在");
        }

        ProofMapper.CycleProofRow cycle =
                mapper.findLatestCycle(batch.greenhouseId());
        ProofMapper.CertificateProofRow certificate =
                mapper.findCertificate(batchId);
        ProofMapper.TraceCodeProofRow traceCode =
                mapper.findTraceCode(batchId);

        String chainStatus = cycle == null
                ? "PENDING"
                : cycle.chainStatus();
        String verificationStatus = verify(
                chainStatus,
                certificate,
                traceCode
        );

        return new ProofView(
                batch.batchId(),
                batch.cropName(),
                batch.greenhouseCode(),
                cycle == null ? null : cycle.cycleId(),
                cycle == null ? null : cycle.rootHash(),
                cycle == null ? null : cycle.chainTxHash(),
                chainStatus,
                certificate == null ? null : certificate.certificateNo(),
                certificate == null ? null : certificate.contentHash(),
                certificate == null ? null : certificate.status(),
                certificate == null ? null : certificate.chainTxHash(),
                traceCode == null ? null : traceCode.productCode(),
                traceCode == null ? null : traceCode.status(),
                traceCode == null ? null : traceCode.chainTxHash(),
                verificationStatus,
                message(verificationStatus)
        );
    }

    private String verify(
            String chainStatus,
            ProofMapper.CertificateProofRow certificate,
            ProofMapper.TraceCodeProofRow traceCode
    ) {
        if (certificate == null || traceCode == null) {
            return "PENDING";
        }
        boolean verified =
                "CONFIRMED".equals(chainStatus)
                        && "VALID".equals(certificate.status())
                        && "ACTIVE".equals(traceCode.status());
        return verified ? "VERIFIED" : "RISK";
    }

    private String message(String verificationStatus) {
        return switch (verificationStatus) {
            case "VERIFIED" -> "环境根哈希、电子合格证和溯源码状态一致";
            case "RISK" -> "存在未确认存证或失效证书/溯源码";
            default -> "存证业务数据尚未完整";
        };
    }
}
