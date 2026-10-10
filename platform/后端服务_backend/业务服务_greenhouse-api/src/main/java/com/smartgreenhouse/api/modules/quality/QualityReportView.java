package com.smartgreenhouse.api.modules.quality;

import java.time.LocalDateTime;

public record QualityReportView(
        Long reportId,
        Long batchId,
        String institutionId,
        String institutionName,
        String reportNo,
        String resultSummary,
        String fileUrl,
        String contentHash,
        LocalDateTime testedAt,
        String status
) {
}

