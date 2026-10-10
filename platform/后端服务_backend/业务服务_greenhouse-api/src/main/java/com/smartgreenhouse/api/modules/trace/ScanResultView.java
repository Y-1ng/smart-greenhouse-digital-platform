package com.smartgreenhouse.api.modules.trace;

import java.time.LocalDateTime;

public record ScanResultView(
        String productCode,
        String result,
        boolean firstScan,
        int scanCount,
        String channel,
        LocalDateTime scannedAt,
        String chainTxHash
) {
}
