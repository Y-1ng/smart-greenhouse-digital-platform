package com.smartgreenhouse.api.modules.greenhouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record GreenhouseOverview(
        String greenhouseCode,
        String greenhouseName,
        BigDecimal area,
        String greenhouseStatus,
        Long cycleId,
        String rootHash,
        String chainStatus,
        Map<String, BigDecimal> metrics,
        CropSnapshot crop,
        List<DeviceSnapshot> devices
) {

    public String cropName() {
        return crop == null ? null : crop.cropName();
    }

    public String varietyName() {
        return crop == null ? null : crop.varietyName();
    }

    public record CropSnapshot(
            Long batchId,
            String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate,
            String status
    ) {
    }

    public record DeviceSnapshot(
            Long deviceId,
            String deviceCode,
            String deviceType,
            String onlineStatus,
            String operatingState,
            String lastSeenAt
    ) {
    }
}
