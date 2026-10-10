package com.smartgreenhouse.api.modules.cooperation;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface CooperationMapper {

    @Select("""
            SELECT
              v.village_id,
              v.village_name,
              v.land_area,
              cs.share_id,
              cs.project_name,
              cs.share_ratio,
              cs.effective_date,
              latest.amount,
              latest.status
            FROM village v
            JOIN cooperation_share cs ON cs.village_id = v.village_id
            LEFT JOIN dividend_record latest ON latest.dividend_id = (
              SELECT dr.dividend_id
              FROM dividend_record dr
              WHERE dr.share_id = cs.share_id
              ORDER BY dr.dividend_id DESC
              LIMIT 1
            )
            ORDER BY v.village_id, cs.share_id
            """)
    @ConstructorArgs({
            @Arg(column = "village_id", javaType = Long.class),
            @Arg(column = "village_name", javaType = String.class),
            @Arg(column = "land_area", javaType = BigDecimal.class),
            @Arg(column = "share_id", javaType = Long.class),
            @Arg(column = "project_name", javaType = String.class),
            @Arg(column = "share_ratio", javaType = BigDecimal.class),
            @Arg(column = "effective_date", javaType = LocalDate.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class)
    })
    List<VillageRow> findVillageShares();

    @Select("""
            SELECT COUNT(*)
            FROM village
            WHERE village_id = #{villageId}
            """)
    int countVillage(Long villageId);

    @Select("""
            SELECT village_id, village_name
            FROM village
            WHERE LOWER(village_name) = LOWER(#{villageName})
            ORDER BY village_id
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "village_id", javaType = Long.class),
            @Arg(column = "village_name", javaType = String.class)
    })
    VillageIdRow findVillageByName(
            @Param("villageName") String villageName
    );

    @Insert("""
            INSERT INTO village (village_name)
            VALUES (#{villageName})
            """)
    int insertVillage(@Param("villageName") String villageName);

    @Insert("""
            INSERT INTO cooperation_share (
              village_id,
              project_name,
              share_ratio,
              effective_date,
              status
            ) VALUES (
              #{villageId},
              #{projectName},
              #{shareRatio},
              #{effectiveDate},
              #{status}
            )
            """)
    int insertShare(ShareInsert share);

    @Select("""
            SELECT COUNT(*)
            FROM cooperation_share
            WHERE share_id = #{shareId}
            """)
    int countShare(Long shareId);

    @Delete("""
            DELETE FROM dividend_record
            WHERE share_id = #{shareId}
            """)
    int deleteDividendsByShareId(@Param("shareId") Long shareId);

    @Delete("""
            DELETE FROM cooperation_share
            WHERE share_id = #{shareId}
            """)
    int deleteShare(@Param("shareId") Long shareId);

    @Insert("""
            INSERT INTO dividend_record (
              share_id,
              period_name,
              amount,
              status,
              confirmed_at
            ) VALUES (
              #{shareId},
              #{periodName},
              #{amount},
              #{status},
              #{confirmedAt}
            )
            """)
    int insertDividend(DividendInsert dividend);

    @Select("""
            SELECT share_id, village_id, project_name, share_ratio,
                   effective_date, status
            FROM cooperation_share
            WHERE project_name = #{projectName}
            ORDER BY share_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "share_id", javaType = Long.class),
            @Arg(column = "village_id", javaType = Long.class),
            @Arg(column = "project_name", javaType = String.class),
            @Arg(column = "share_ratio", javaType = BigDecimal.class),
            @Arg(column = "effective_date", javaType = LocalDate.class),
            @Arg(column = "status", javaType = String.class)
    })
    ShareRow findShareByProjectName(String projectName);

    @Select("""
            SELECT dividend_id, share_id, period_name, amount, status,
                   confirmed_at
            FROM dividend_record
            WHERE share_id = #{shareId}
              AND period_name = #{periodName}
            ORDER BY dividend_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "dividend_id", javaType = Long.class),
            @Arg(column = "share_id", javaType = Long.class),
            @Arg(column = "period_name", javaType = String.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "confirmed_at", javaType = LocalDateTime.class)
    })
    DividendRow findDividend(
            @Param("shareId") Long shareId,
            @Param("periodName") String periodName
    );

    record VillageIdRow(
            Long villageId,
            String villageName
    ) {
    }

    record VillageRow(
            Long villageId,
            String villageName,
            BigDecimal landArea,
            Long shareId,
            String projectName,
            BigDecimal shareRatio,
            LocalDate effectiveDate,
            BigDecimal dividendAmount,
            String status
    ) {
    }

    record ShareInsert(
            Long villageId,
            String projectName,
            BigDecimal shareRatio,
            LocalDate effectiveDate,
            String status
    ) {
    }

    record DividendInsert(
            Long shareId,
            String periodName,
            BigDecimal amount,
            String status,
            LocalDateTime confirmedAt
    ) {
    }

    record ShareRow(
            Long shareId,
            Long villageId,
            String projectName,
            BigDecimal shareRatio,
            LocalDate effectiveDate,
            String status
    ) {
    }

    record DividendRow(
            Long dividendId,
            Long shareId,
            String periodName,
            BigDecimal amount,
            String status,
            LocalDateTime confirmedAt
    ) {
    }
}
