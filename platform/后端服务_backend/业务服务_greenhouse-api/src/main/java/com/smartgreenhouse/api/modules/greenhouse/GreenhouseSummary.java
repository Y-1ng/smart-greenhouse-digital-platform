package com.smartgreenhouse.api.modules.greenhouse;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GreenhouseSummary(
        Long greenhouseId,
        String greenhouseCode,
        String greenhouseName,
        BigDecimal area,
        String location,
        String status,
        Long batchId,
        String cropName,
        String varietyName,
        LocalDate plantingDate,
        LocalDate expectedHarvestDate
) {
}
