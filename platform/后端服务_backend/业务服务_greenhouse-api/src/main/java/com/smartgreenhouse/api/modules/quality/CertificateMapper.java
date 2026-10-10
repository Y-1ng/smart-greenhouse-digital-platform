package com.smartgreenhouse.api.modules.quality;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CertificateMapper {

    @Select("""
            SELECT COUNT(*)
            FROM crop_batch
            WHERE batch_id = #{batchId}
            """)
    int countBatch(@Param("batchId") Long batchId);

    @Select("""
            SELECT
              certificate_id,
              batch_id,
              certificate_no,
              content_hash,
              chain_tx_hash,
              status,
              issued_at,
              revoked_at
            FROM certificate
            WHERE certificate_no = #{certificateNo}
            """)
    @ConstructorArgs({
            @Arg(column = "certificate_id", javaType = Long.class),
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "certificate_no", javaType = String.class),
            @Arg(column = "content_hash", javaType = String.class),
            @Arg(column = "chain_tx_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "issued_at", javaType = LocalDateTime.class),
            @Arg(column = "revoked_at", javaType = LocalDateTime.class)
    })
    CertificateRow findByCertificateNo(
            @Param("certificateNo") String certificateNo
    );

    @Insert("""
            INSERT INTO certificate (
              batch_id,
              certificate_no,
              content_hash,
              status,
              issued_at
            ) VALUES (
              #{batchId},
              #{certificateNo},
              #{contentHash},
              #{status},
              #{issuedAt}
            )
            """)
    int insert(CertificateInsert certificate);

    @Update("""
            UPDATE certificate
            SET chain_tx_hash = #{chainTxHash}
            WHERE certificate_id = #{certificateId}
            """)
    int updateChainTxHash(
            @Param("certificateId") Long certificateId,
            @Param("chainTxHash") String chainTxHash
    );

    record CertificateRow(
            Long certificateId,
            Long batchId,
            String certificateNo,
            String contentHash,
            String chainTxHash,
            String status,
            LocalDateTime issuedAt,
            LocalDateTime revokedAt
    ) {
    }

    record CertificateInsert(
            Long batchId,
            String certificateNo,
            String contentHash,
            String status,
            LocalDateTime issuedAt
    ) {
    }
}
