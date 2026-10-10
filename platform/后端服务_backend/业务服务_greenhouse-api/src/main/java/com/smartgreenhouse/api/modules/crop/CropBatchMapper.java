package com.smartgreenhouse.api.modules.crop;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CropBatchMapper {

    @Select("""
            SELECT
              cb.batch_id,
              cb.crop_name,
              cb.variety_name,
              cb.planting_date,
              cb.expected_harvest_date,
              cb.status,
              g.greenhouse_code,
              g.greenhouse_name
            FROM crop_batch cb
            JOIN greenhouse g ON g.greenhouse_id = cb.greenhouse_id
            ORDER BY cb.batch_id
            """)
    @ConstructorArgs({
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class),
            @Arg(column = "variety_name", javaType = String.class),
            @Arg(column = "planting_date", javaType = LocalDate.class),
            @Arg(column = "expected_harvest_date", javaType = LocalDate.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "greenhouse_name", javaType = String.class)
    })
    List<CropBatchRow> findAll();

    record CropBatchRow(
            Long batchId,
            String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate,
            String status,
            String greenhouseCode,
            String greenhouseName
    ) {
    }
}
