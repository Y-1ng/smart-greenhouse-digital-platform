package com.smartgreenhouse.api.modules.storage;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "app.storage.provider",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(
            @Value("${app.storage.local-root:./data/files}") String root
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        String safeCategory = category.replaceAll("[^a-zA-Z0-9_-]", "_");
        String originalFilename = file.getOriginalFilename() == null
                ? "file"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        String storageKey = safeCategory + "/" + UUID.randomUUID()
                + "_" + originalFilename;
        Path target = resolve(storageKey);

        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (
                    InputStream input = file.getInputStream();
                    DigestInputStream digestInput =
                            new DigestInputStream(input, digest)
            ) {
                Files.copy(digestInput, target);
            }
            return new StoredFile(
                    storageKey,
                    "/files/" + storageKey.replace('\\', '/'),
                    HexFormat.of().formatHex(digest.digest()),
                    originalFilename,
                    file.getSize(),
                    file.getContentType()
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "文件保存失败"
            );
        }
    }

    @Override
    public StoredFileContent load(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }

        try {
            String storedName = target.getFileName().toString();
            int separator = storedName.indexOf('_');
            String originalFilename = separator >= 0
                    ? storedName.substring(separator + 1)
                    : storedName;
            String contentType = Files.probeContentType(target);
            return new StoredFileContent(
                    new FileSystemResource(target),
                    originalFilename,
                    contentType == null
                            ? "application/octet-stream"
                            : contentType,
                    Files.size(target)
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "文件读取失败"
            );
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件标识不能为空");
        }

        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
        return target;
    }
}
