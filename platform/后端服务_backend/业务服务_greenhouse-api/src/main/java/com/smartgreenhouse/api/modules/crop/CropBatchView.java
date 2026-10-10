package com.smartgreenhouse.api.modules.crop;

import java.time.LocalDate;

public record CropBatchView(
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
