package com.smartgreenhouse.api.modules.storage;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "app.storage.provider",
        havingValue = "minio"
)
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioFileStorageService(
            @Value("${app.storage.minio-endpoint}") String endpoint,
            @Value("${app.storage.minio-access-key}") String accessKey,
            @Value("${app.storage.minio-secret-key}") String secretKey,
            @Value("${app.storage.minio-bucket:smart-greenhouse}") String bucket
    ) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
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

        try {
            ensureBucket();
            byte[] content = file.getBytes();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .contentType(file.getContentType() == null
                            ? "application/octet-stream"
                            : file.getContentType())
                    .stream(
                            new ByteArrayInputStream(content),
                            content.length,
                            -1
                    )
                    .build());
            return new StoredFile(
                    storageKey,
                    "/files/" + storageKey,
                    sha256(content),
                    originalFilename,
                    content.length,
                    file.getContentType()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "MinIO 文件保存失败"
            );
        }
    }

    @Override
    public StoredFileContent load(String storageKey) {
        try {
            GetObjectResponse response = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .build()
            );
            String storedName = Path.of(storageKey).getFileName().toString();
            int separator = storedName.indexOf('_');
            String originalFilename = separator >= 0
                    ? storedName.substring(separator + 1)
                    : storedName;
            String contentLength = response.headers().get("Content-Length");
            long size = contentLength == null
                    ? -1
                    : Long.parseLong(contentLength);
            String contentType = response.headers().get("Content-Type");
            return new StoredFileContent(
                    new InputStreamResource(response),
                    originalFilename,
                    contentType == null
                            ? "application/octet-stream"
                            : contentType,
                    size
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "MinIO 文件不存在");
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            if (!client.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build())) {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build());
            }
            bucketReady = true;
        }
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
