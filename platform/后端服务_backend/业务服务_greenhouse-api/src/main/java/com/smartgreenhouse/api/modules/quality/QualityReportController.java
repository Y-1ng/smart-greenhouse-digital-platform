package com.smartgreenhouse.api.modules.quality;

import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/quality/reports")
public class QualityReportController {

    private final QualityReportService service;

    public QualityReportController(QualityReportService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ApiResponse<QualityReportView> upload(
            @RequestParam Long batchId,
            @RequestParam String institutionId,
            @RequestParam String institutionName,
            @RequestParam String reportNo,
            @RequestParam String resultSummary,
            @RequestParam String testedAt,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(service.upload(
                batchId,
                institutionId,
                institutionName,
                reportNo,
                resultSummary,
                LocalDateTime.parse(testedAt),
                file
        ));
    }

    @GetMapping("/{reportNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ApiResponse<QualityReportView> get(
            @PathVariable String reportNo
    ) {
        return ApiResponse.success(service.get(reportNo));
    }

    @GetMapping("/{reportNo}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ResponseEntity<Resource> download(
            @PathVariable String reportNo
    ) {
        QualityReportDownload download = service.download(reportNo);
        String disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(download.mediaType())
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(download.resource());
    }
}
