package com.smartgreenhouse.api.modules.quality;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.security.MessageDigest;

@Service
public class CertificateVerifyService {

    private final CertificateVerifyMapper mapper;

    public CertificateVerifyService(CertificateVerifyMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public CertificateVerifyResult verify(
            CertificateController.VerifyRequest request
    ) {
        String canonical = canonical(request);
        String canonicalDigest = sha256(canonical);
        boolean verified = verifySignature(
                canonical,
                request.publicKey(),
                request.signature()
        );
        Instant verifiedAt = Instant.now();
        Long certificateId = mapper.findCertificateId(request.certificateNo());
        String failureReason = verified ? null : "签名无效";

        mapper.insert(new CertificateVerifyMapper.VerificationLog(
                request.requestNo(),
                certificateId,
                request.certificateNo(),
                request.publicKeyId(),
                request.publicKeyId(),
                canonicalDigest,
                request.signature(),
                verified ? 1 : 0,
                failureReason,
                LocalDateTime.ofInstant(verifiedAt, ZoneId.systemDefault())
        ));

        return new CertificateVerifyResult(
                request.requestNo(),
                request.certificateNo(),
                verified,
                canonicalDigest,
                verified ? "验签通过" : "签名无效",
                verifiedAt
        );
    }

    private boolean verifySignature(
            String canonical,
            String publicKeyBase64,
            String signatureBase64
    ) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception exception) {
            return false;
        }
    }

    private String canonical(CertificateController.VerifyRequest request) {
        return String.join(
                "\n",
                request.certificateNo(),
                Long.toString(request.batchId()),
                request.institutionId(),
                request.contentHash(),
                request.issuedAt()
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

