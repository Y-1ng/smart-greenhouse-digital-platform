package com.smartgreenhouse.api.modules.storage;

import org.springframework.core.io.Resource;

public record StoredFileContent(
        Resource resource,
        String originalFilename,
        String contentType,
        long size
) {
}
