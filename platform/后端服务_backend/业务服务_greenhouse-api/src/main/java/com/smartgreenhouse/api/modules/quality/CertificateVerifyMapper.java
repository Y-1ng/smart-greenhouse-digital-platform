package com.smartgreenhouse.api.modules.quality;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface CertificateVerifyMapper {

    @Select("""
            SELECT certificate_id
            FROM certificate
            WHERE certificate_no = #{certificateNo}
            """)
    Long findCertificateId(@Param("certificateNo") String certificateNo);

    @Insert("""
            INSERT INTO certificate_sign_log (
              request_no,
              certificate_id,
              certificate_no,
              public_key_id,
              key_id,
              canonical_digest,
              signature_value,
              verify_result,
              failure_reason,
              verified_at
            ) VALUES (
              #{requestNo},
              #{certificateId},
              #{certificateNo},
              #{publicKeyId},
              #{keyId},
              #{canonicalDigest},
              #{signatureValue},
              #{verifyResult},
              #{failureReason},
              #{verifiedAt}
            )
            """)
    int insert(VerificationLog verificationLog);

    record VerificationLog(
            String requestNo,
            Long certificateId,
            String certificateNo,
            String publicKeyId,
            String keyId,
            String canonicalDigest,
            String signatureValue,
            int verifyResult,
            String failureReason,
            LocalDateTime verifiedAt
    ) {
    }
}

