package com.smartgreenhouse.api.modules.trace;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TraceMapper {

    @Select("""
            SELECT
              tc.trace_code_id,
              tc.product_code,
              tc.field_no,
              tc.public_key_id,
              tc.signature_value,
              tc.status AS trace_status,
              cb.batch_id,
              c.certificate_no,
              c.status AS certificate_status,
              ec.cycle_id AS environment_cycle_id,
              ec.cycle_start AS environment_cycle_start,
              ec.cycle_end AS environment_cycle_end,
              ec.root_hash,
              ec.data_digest,
              ec.chain_status AS environment_chain_status
            FROM trace_code tc
            JOIN crop_batch cb ON cb.batch_id = tc.batch_id
            LEFT JOIN certificate c ON c.batch_id = cb.batch_id
            LEFT JOIN greenhouse g ON g.greenhouse_id = cb.greenhouse_id
            LEFT JOIN environment_cycle ec ON ec.greenhouse_id = g.greenhouse_id
            WHERE tc.product_code = #{productCode}
            ORDER BY ec.cycle_end DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "trace_code_id", javaType = Long.class),
            @Arg(column = "product_code", javaType = String.class),
            @Arg(column = "field_no", javaType = String.class),
            @Arg(column = "public_key_id", javaType = String.class),
            @Arg(column = "signature_value", javaType = String.class),
            @Arg(column = "trace_status", javaType = String.class),
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "certificate_no", javaType = String.class),
            @Arg(column = "certificate_status", javaType = String.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "environment_cycle_id", javaType = Long.class),
            @Arg(column = "environment_cycle_start", javaType = LocalDateTime.class),
            @Arg(column = "environment_cycle_end", javaType = LocalDateTime.class),
            @Arg(column = "data_digest", javaType = String.class),
            @Arg(column = "environment_chain_status", javaType = String.class)
    })
    TraceRow findByProductCode(@Param("productCode") String productCode);

    @Select("""
            SELECT metric_code, metric_value, unit, collected_at
            FROM sensor_reading
            WHERE cycle_id = #{cycleId}
            ORDER BY reading_id
            """)
    @ConstructorArgs({
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "collected_at", javaType = LocalDateTime.class)
    })
    List<CycleMetricRow> findCycleMetrics(@Param("cycleId") Long cycleId);

    @Select("""
            SELECT node_id, stage_type, previous_hash, payload_hash,
                   chain_tx_hash, created_at
            FROM trace_node
            WHERE field_no = #{fieldNo}
            ORDER BY node_id
            """)
    @ConstructorArgs({
            @Arg(column = "node_id", javaType = Long.class),
            @Arg(column = "stage_type", javaType = String.class),
            @Arg(column = "previous_hash", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "chain_tx_hash", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<TraceNodeRow> findTraceNodes(@Param("fieldNo") String fieldNo);

    /**
     * 每个田块只返回“前序节点都已上链”的那一个待上链节点：
     * 链上追加节点要求 previousHash 等于链上链尾，跳序提交会被合约拒绝。
     */
    @Select("""
            SELECT t.node_id, t.field_no, t.stage_type,
                   (SELECT COUNT(*) FROM trace_node idx
                     WHERE idx.field_no = t.field_no
                       AND idx.node_id < t.node_id) AS node_index,
                   t.previous_hash, t.payload_hash
            FROM trace_node t
            WHERE t.chain_tx_hash IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM trace_node earlier
                WHERE earlier.field_no = t.field_no
                  AND earlier.node_id < t.node_id
                  AND earlier.chain_tx_hash IS NULL
              )
            ORDER BY t.node_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "node_id", javaType = Long.class),
            @Arg(column = "field_no", javaType = String.class),
            @Arg(column = "stage_type", javaType = String.class),
            @Arg(column = "node_index", javaType = Integer.class),
            @Arg(column = "previous_hash", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class)
    })
    List<PendingNode> findPendingNodes(@Param("limit") int limit);

    @Select("""
            SELECT t.node_id, t.field_no, t.stage_type,
                   (SELECT COUNT(*) FROM trace_node idx
                     WHERE idx.field_no = t.field_no
                       AND idx.node_id < t.node_id) AS node_index,
                   t.previous_hash, t.payload_hash
            FROM trace_node t
            WHERE t.field_no = #{fieldNo}
              AND t.chain_tx_hash IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM trace_node earlier
                WHERE earlier.field_no = t.field_no
                  AND earlier.node_id < t.node_id
                  AND earlier.chain_tx_hash IS NULL
              )
            ORDER BY t.node_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "node_id", javaType = Long.class),
            @Arg(column = "field_no", javaType = String.class),
            @Arg(column = "stage_type", javaType = String.class),
            @Arg(column = "node_index", javaType = Integer.class),
            @Arg(column = "previous_hash", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class)
    })
    List<PendingNode> findPendingNodesByField(
            @Param("fieldNo") String fieldNo,
            @Param("limit") int limit
    );

    @Select("SELECT COUNT(*) FROM trace_node WHERE chain_tx_hash IS NULL")
    int countPendingNodes();

    @Update("""
            UPDATE trace_node
            SET chain_tx_hash = #{chainTxHash}
            WHERE node_id = #{nodeId}
            """)
    int updateNodeChainTxHash(
            @Param("nodeId") Long nodeId,
            @Param("chainTxHash") String chainTxHash
    );

    @Insert("""
            INSERT INTO trace_node (
              field_no, stage_type, previous_hash, payload_hash,
              chain_tx_hash, created_at
            ) VALUES (
              #{fieldNo}, #{stageType}, #{previousHash}, #{payloadHash},
              NULL, #{createdAt}
            )
            """)
    int insertTraceNode(TraceNodeInsert node);

    @Select("""
            SELECT node_id, stage_type, previous_hash, payload_hash,
                   chain_tx_hash, created_at
            FROM trace_node
            WHERE field_no = #{fieldNo} AND payload_hash = #{payloadHash}
            ORDER BY node_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "node_id", javaType = Long.class),
            @Arg(column = "stage_type", javaType = String.class),
            @Arg(column = "previous_hash", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "chain_tx_hash", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    TraceNodeRow findNodeByPayloadHash(
            @Param("fieldNo") String fieldNo,
            @Param("payloadHash") String payloadHash
    );

    @Select("SELECT COUNT(*) FROM trace_code WHERE field_no = #{fieldNo}")
    int countTraceCodesByField(@Param("fieldNo") String fieldNo);

    @Select("""
            SELECT COUNT(*)
            FROM scan_record
            WHERE trace_code_id = #{traceCodeId}
            """)
    int countScans(@Param("traceCodeId") Long traceCodeId);

    @Insert("""
            INSERT INTO scan_record (
              trace_code_id,
              scan_time,
              scan_channel,
              ip_hash,
              risk_result,
              chain_tx_hash
            ) VALUES (
              #{traceCodeId},
              #{scanTime},
              #{channel},
              #{deviceHash},
              #{riskResult},
              #{chainTxHash}
            )
            """)
    int insertScan(ScanInsert scan);

    @Select("""
            SELECT COUNT(*)
            FROM crop_batch
            WHERE batch_id = #{batchId}
            """)
    int countBatch(@Param("batchId") Long batchId);

    @Insert("""
            INSERT INTO trace_code (
              batch_id,
              field_no,
              product_code,
              public_key_id,
              signature_value,
              status
            ) VALUES (
              #{batchId},
              #{fieldNo},
              #{productCode},
              #{publicKeyId},
              #{signatureValue},
              'ACTIVE'
            )
            """)
    int insertTraceCode(TraceCodeInsert traceCode);

    @Update("""
            UPDATE trace_code
            SET chain_tx_hash = #{chainTxHash}
            WHERE product_code = #{productCode}
            """)
    int updateChainTxHash(
            @Param("productCode") String productCode,
            @Param("chainTxHash") String chainTxHash
    );

    @Select("""
            SELECT product_code
            FROM trace_code
            WHERE field_no = #{fieldNo}
            ORDER BY trace_code_id DESC
            LIMIT 1
            """)
    String findProductCodeByField(@Param("fieldNo") String fieldNo);

    record TraceRow(
            Long traceCodeId,
            String productCode,
            String fieldNo,
            String publicKeyId,
            String signatureValue,
            String traceStatus,
            Long batchId,
            String certificateNo,
            String certificateStatus,
            String rootHash,
            Long environmentCycleId,
            LocalDateTime environmentCycleStart,
            LocalDateTime environmentCycleEnd,
            String dataDigest,
            String environmentChainStatus
    ) {
    }

    record CycleMetricRow(
            String metricCode,
            BigDecimal metricValue,
            String unit,
            LocalDateTime collectedAt
    ) {
    }

    record TraceNodeRow(
            Long nodeId,
            String stageType,
            String previousHash,
            String payloadHash,
            String chainTxHash,
            LocalDateTime createdAt
    ) {
    }

    record PendingNode(
            Long nodeId,
            String fieldNo,
            String stageType,
            Integer index,
            String previousHash,
            String payloadHash
    ) {
    }

    record TraceNodeInsert(
            String fieldNo,
            String stageType,
            String previousHash,
            String payloadHash,
            LocalDateTime createdAt
    ) {
    }

    record ScanInsert(
            Long traceCodeId,
            LocalDateTime scanTime,
            String channel,
            String deviceHash,
            String riskResult,
            String chainTxHash
    ) {
    }

    record TraceCodeInsert(
            Long batchId,
            String fieldNo,
            String productCode,
            String publicKeyId,
            String signatureValue
    ) {
    }

}
