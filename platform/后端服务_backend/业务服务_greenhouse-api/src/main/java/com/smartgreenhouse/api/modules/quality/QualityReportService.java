package com.smartgreenhouse.api.modules.quality;

import com.smartgreenhouse.api.modules.storage.FileStorageService;
import com.smartgreenhouse.api.modules.storage.StoredFile;
import com.smartgreenhouse.api.modules.storage.StoredFileContent;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class QualityReportService {

    private final QualityReportMapper mapper;
    private final FileStorageService fileStorageService;

    public QualityReportService(
            QualityReportMapper mapper,
            FileStorageService fileStorageService
    ) {
        this.mapper = mapper;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public QualityReportView upload(
            Long batchId,
            String institutionId,
            String institutionName,
            String reportNo,
            String resultSummary,
            LocalDateTime testedAt,
            MultipartFile file
    ) {
        if (mapper.findByReportNo(reportNo) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "检测报告编号已存在");
        }

        StoredFile storedFile = fileStorageService.store(
                file,
                "quality-reports"
        );
        mapper.insert(new QualityReportMapper.QualityReportInsert(
                batchId,
                institutionId,
                institutionName,
                reportNo,
                resultSummary,
                storedFile.url(),
                storedFile.sha256(),
                testedAt
        ));
        return get(reportNo);
    }

    @Transactional(readOnly = true)
    public QualityReportView get(String reportNo) {
        QualityReportMapper.QualityReportRow report = requireReport(reportNo);
        return new QualityReportView(
                report.reportId(),
                report.batchId(),
                report.institutionId(),
                report.institutionName(),
                report.reportNo(),
                report.resultSummary(),
                report.fileUrl(),
                report.contentHash(),
                report.testedAt(),
                "STORED"
        );
    }

    @Transactional(readOnly = true)
    public QualityReportDownload download(String reportNo) {
        QualityReportMapper.QualityReportRow report = requireReport(reportNo);
        String fileUrl = report.fileUrl();
        if (fileUrl == null || !fileUrl.startsWith("/files/")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告文件不存在");
        }

        StoredFileContent content = fileStorageService.load(
                fileUrl.substring("/files/".length())
        );
        return new QualityReportDownload(
                report.reportNo(),
                content.originalFilename(),
                parseMediaType(content.contentType()),
                content.size(),
                content.resource()
        );
    }

    private QualityReportMapper.QualityReportRow requireReport(String reportNo) {
        QualityReportMapper.QualityReportRow report =
                mapper.findByReportNo(reportNo);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "检测报告不存在");
        }
        return report;
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
