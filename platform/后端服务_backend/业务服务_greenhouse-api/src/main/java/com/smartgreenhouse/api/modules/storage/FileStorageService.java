package com.smartgreenhouse.api.modules.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile store(MultipartFile file, String category);

    StoredFileContent load(String storageKey);
}
