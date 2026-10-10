package com.smartgreenhouse.api.modules.chain;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 链上待补偿记录的查询与回执回写。
 *
 * 待补偿判定口径：环境周期看 chain_status = PENDING，其余存证表看 chain_tx_hash 为空，
 * 与业务写入时「链上失败只保留 PENDING、不回滚业务」的策略保持一致。
 */
@Mapper
public interface ChainCompensationMapper {

    @Select("""
            SELECT
              c.cycle_id,
              g.greenhouse_code,
              c.cycle_start,
              c.cycle_end,
              c.root_hash,
              c.data_digest
            FROM environment_cycle c
            JOIN greenhouse g ON g.greenhouse_id = c.greenhouse_id
            WHERE c.chain_status = 'PENDING' AND c.chain_tx_hash IS NULL
            ORDER BY c.cycle_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "cycle_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "cycle_start", javaType = LocalDateTime.class),
            @Arg(column = "cycle_end", javaType = LocalDateTime.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "data_digest", javaType = String.class)
    })
    List<PendingCycle> findPendingCycles(@Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM environment_cycle
            WHERE chain_status = 'PENDING' AND chain_tx_hash IS NULL
            """)
    int countPendingCycles();

    @Update("""
            UPDATE environment_cycle
            SET chain_tx_hash = #{transactionHash}, chain_status = 'CONFIRMED'
            WHERE cycle_id = #{cycleId}
            """)
    int confirmCycle(
            @Param("cycleId") Long cycleId,
            @Param("transactionHash") String transactionHash
    );

    @Select("""
            SELECT certificate_id, content_hash, status
            FROM certificate
            WHERE chain_tx_hash IS NULL
            ORDER BY certificate_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "certificate_id", javaType = Long.class),
            @Arg(column = "content_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    List<PendingCertificate> findPendingCertificates(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM certificate WHERE chain_tx_hash IS NULL")
    int countPendingCertificates();

    @Update("""
            UPDATE certificate
            SET chain_tx_hash = #{transactionHash}
            WHERE certificate_id = #{certificateId}
            """)
    int confirmCertificate(
            @Param("certificateId") Long certificateId,
            @Param("transactionHash") String transactionHash
    );

    @Select("""
            SELECT trace_code_id, product_code, signature_value, batch_id
            FROM trace_code
            WHERE chain_tx_hash IS NULL
            ORDER BY trace_code_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "trace_code_id", javaType = Long.class),
            @Arg(column = "product_code", javaType = String.class),
            @Arg(column = "signature_value", javaType = String.class),
            @Arg(column = "batch_id", javaType = Long.class)
    })
    List<PendingTraceCode> findPendingTraceCodes(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM trace_code WHERE chain_tx_hash IS NULL")
    int countPendingTraceCodes();

    @Update("""
            UPDATE trace_code
            SET chain_tx_hash = #{transactionHash}
            WHERE trace_code_id = #{traceCodeId}
            """)
    int confirmTraceCode(
            @Param("traceCodeId") Long traceCodeId,
            @Param("transactionHash") String transactionHash
    );

    @Select("""
            SELECT sr.scan_id, tc.product_code, sr.scan_channel, sr.risk_result
            FROM scan_record sr
            JOIN trace_code tc ON tc.trace_code_id = sr.trace_code_id
            WHERE sr.chain_tx_hash IS NULL
              AND tc.chain_tx_hash IS NOT NULL
            ORDER BY sr.scan_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "scan_id", javaType = Long.class),
            @Arg(column = "product_code", javaType = String.class),
            @Arg(column = "scan_channel", javaType = String.class),
            @Arg(column = "risk_result", javaType = String.class)
    })
    List<PendingScan> findPendingScans(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM scan_record WHERE chain_tx_hash IS NULL")
    int countPendingScans();

    @Select("SELECT COUNT(*) FROM trace_node WHERE chain_tx_hash IS NULL")
    int countPendingTraceNodes();

    /**
     * 全平台存证记录总数：五类存证表行数之和。
     * 界面上的「存证记录」用它，不再是写死的演示数字。
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM environment_cycle)
              + (SELECT COUNT(*) FROM certificate)
              + (SELECT COUNT(*) FROM trace_code)
              + (SELECT COUNT(*) FROM trace_node)
              + (SELECT COUNT(*) FROM scan_record)
            """)
    int countProofs();

    @Update("""
            UPDATE scan_record
            SET chain_tx_hash = #{transactionHash}
            WHERE scan_id = #{scanId}
            """)
    int confirmScan(
            @Param("scanId") Long scanId,
            @Param("transactionHash") String transactionHash
    );

    record PendingCycle(
            Long cycleId,
            String greenhouseCode,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd,
            String rootHash,
            String dataDigest
    ) {
    }

    record PendingCertificate(
            Long certificateId,
            String contentHash,
            String status
    ) {
    }

    record PendingTraceCode(
            Long traceCodeId,
            String productCode,
            String signatureValue,
            Long batchId
    ) {
    }

    record PendingScan(
            Long scanId,
            String productCode,
            String channel,
            String result
    ) {
    }
}
