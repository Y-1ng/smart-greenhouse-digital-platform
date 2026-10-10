package com.smartgreenhouse.api.modules.quality;

import com.smartgreenhouse.api.integration.chain.ChainInvoker;
import com.smartgreenhouse.api.integration.chain.ChainReceipt;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CertificateManagementService {

    private final CertificateMapper mapper;
    private final ChainInvoker chainInvoker;

    public CertificateManagementService(
            CertificateMapper mapper,
            ChainInvoker chainInvoker
    ) {
        this.mapper = mapper;
        this.chainInvoker = chainInvoker;
    }

    @Transactional
    public CertificateView create(
            CertificateController.CertificateCreateRequest request
    ) {
        if (mapper.countBatch(request.batchId()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作物批次不存在");
        }
        if (mapper.findByCertificateNo(request.certificateNo()) != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "合格证编号已存在"
            );
        }

        String status = request.status() == null
                || request.status().isBlank()
                ? "DRAFT"
                : request.status().toUpperCase();
        LocalDateTime issuedAt = "VALID".equals(status)
                || "ISSUED".equals(status)
                ? LocalDateTime.now()
                : null;
        mapper.insert(new CertificateMapper.CertificateInsert(
                request.batchId(),
                request.certificateNo(),
                request.contentHash(),
                status,
                issuedAt
        ));

        CertificateMapper.CertificateRow created =
                mapper.findByCertificateNo(request.certificateNo());
        ChainReceipt receipt = chainInvoker.recordCertificate(
                String.valueOf(created.certificateId()),
                created.contentHash(),
                created.status()
        );
        if (receipt.transactionHash() != null) {
            mapper.updateChainTxHash(
                    created.certificateId(),
                    receipt.transactionHash()
            );
        }

        return toView(mapper.findByCertificateNo(request.certificateNo()));
    }

    private CertificateView toView(
            CertificateMapper.CertificateRow certificate
    ) {
        return new CertificateView(
                certificate.certificateId(),
                certificate.batchId(),
                certificate.certificateNo(),
                certificate.contentHash(),
                certificate.chainTxHash(),
                certificate.status(),
                certificate.issuedAt(),
                certificate.revokedAt()
        );
    }
}
