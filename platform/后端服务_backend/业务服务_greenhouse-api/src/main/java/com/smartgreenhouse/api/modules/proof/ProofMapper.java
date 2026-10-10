package com.smartgreenhouse.api.modules.proof;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProofMapper {

    @Select("""
            SELECT
              cb.batch_id,
              cb.crop_name,
              g.greenhouse_id,
              g.greenhouse_code
            FROM crop_batch cb
            JOIN greenhouse g ON g.greenhouse_id = cb.greenhouse_id
            WHERE cb.batch_id = #{batchId}
            """)
    @ConstructorArgs({
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class),
            @Arg(column = "greenhouse_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class)
    })
    BatchRow findBatch(@Param("batchId") Long batchId);

    @Select("""
            SELECT
              cycle_id,
              root_hash,
              chain_tx_hash,
              chain_status
            FROM environment_cycle
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY cycle_end DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "cycle_id", javaType = Long.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "chain_tx_hash", javaType = String.class),
            @Arg(column = "chain_status", javaType = String.class)
    })
    CycleProofRow findLatestCycle(@Param("greenhouseId") Long greenhouseId);

    @Select("""
            SELECT
              certificate_no,
              content_hash,
              chain_tx_hash,
              status
            FROM certificate
            WHERE batch_id = #{batchId}
            ORDER BY certificate_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "certificate_no", javaType = String.class),
            @Arg(column = "content_hash", javaType = String.class),
            @Arg(column = "chain_tx_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    CertificateProofRow findCertificate(@Param("batchId") Long batchId);

    @Select("""
            SELECT product_code, status, chain_tx_hash
            FROM trace_code
            WHERE batch_id = #{batchId}
            ORDER BY trace_code_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "product_code", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "chain_tx_hash", javaType = String.class)
    })
    TraceCodeProofRow findTraceCode(@Param("batchId") Long batchId);

    record BatchRow(
            Long batchId,
            String cropName,
            Long greenhouseId,
            String greenhouseCode
    ) {
    }

    record CycleProofRow(
            Long cycleId,
            String rootHash,
            String chainTxHash,
            String chainStatus
    ) {
    }

    record CertificateProofRow(
            String certificateNo,
            String contentHash,
            String chainTxHash,
            String status
    ) {
    }

    record TraceCodeProofRow(
            String productCode,
            String status,
            String chainTxHash
    ) {
    }
}
