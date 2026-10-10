package com.smartgreenhouse.api.modules.quality;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record QualityReportDownload(
        String reportNo,
        String filename,
        MediaType mediaType,
        long size,
        Resource resource
) {
}
