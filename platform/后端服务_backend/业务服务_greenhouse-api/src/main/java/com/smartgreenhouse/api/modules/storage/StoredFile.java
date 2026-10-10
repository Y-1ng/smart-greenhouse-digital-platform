package com.smartgreenhouse.api.modules.storage;

public record StoredFile(
        String storageKey,
        String url,
        String sha256,
        String originalFilename,
        long size,
        String contentType
) {
}

