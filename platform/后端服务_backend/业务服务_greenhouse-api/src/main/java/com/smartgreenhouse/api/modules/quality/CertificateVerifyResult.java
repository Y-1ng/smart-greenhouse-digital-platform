package com.smartgreenhouse.api.modules.quality;

import java.time.Instant;

public record CertificateVerifyResult(
        String requestNo,
        String certificateNo,
        boolean verified,
        String canonicalDigest,
        String message,
        Instant verifiedAt
) {
}

