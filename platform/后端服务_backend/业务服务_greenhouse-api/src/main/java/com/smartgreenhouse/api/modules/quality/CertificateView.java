package com.smartgreenhouse.api.modules.quality;

import java.time.LocalDateTime;

public record CertificateView(
        Long certificateId,
        Long batchId,
        String certificateNo,
        String contentHash,
        String chainTxHash,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime revokedAt
) {
}
