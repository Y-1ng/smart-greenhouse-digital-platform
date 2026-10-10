package com.smartgreenhouse.api.modules.quality;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface QualityReportMapper {

    @Insert("""
            INSERT INTO quality_report (
              batch_id,
              institution_id,
              institution_name,
              report_no,
              result_summary,
              file_url,
              content_hash,
              tested_at
            ) VALUES (
              #{batchId},
              #{institutionId},
              #{institutionName},
              #{reportNo},
              #{resultSummary},
              #{fileUrl},
              #{contentHash},
              #{testedAt}
            )
            """)
    int insert(QualityReportInsert report);

    @Select("""
            SELECT
              report_id,
              batch_id,
              institution_id,
              institution_name,
              report_no,
              result_summary,
              file_url,
              content_hash,
              tested_at
            FROM quality_report
            WHERE report_no = #{reportNo}
            """)
    @ConstructorArgs({
            @Arg(column = "report_id", javaType = Long.class),
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "institution_id", javaType = String.class),
            @Arg(column = "institution_name", javaType = String.class),
            @Arg(column = "report_no", javaType = String.class),
            @Arg(column = "result_summary", javaType = String.class),
            @Arg(column = "file_url", javaType = String.class),
            @Arg(column = "content_hash", javaType = String.class),
            @Arg(column = "tested_at", javaType = LocalDateTime.class)
    })
    QualityReportRow findByReportNo(@Param("reportNo") String reportNo);

    record QualityReportInsert(
            Long batchId,
            String institutionId,
            String institutionName,
            String reportNo,
            String resultSummary,
            String fileUrl,
            String contentHash,
            LocalDateTime testedAt
    ) {
    }

    record QualityReportRow(
            Long reportId,
            Long batchId,
            String institutionId,
            String institutionName,
            String reportNo,
            String resultSummary,
            String fileUrl,
            String contentHash,
            LocalDateTime testedAt
    ) {
    }
}

