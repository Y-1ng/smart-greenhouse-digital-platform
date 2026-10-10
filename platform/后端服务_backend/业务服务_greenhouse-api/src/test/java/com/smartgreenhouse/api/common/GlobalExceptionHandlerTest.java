package com.smartgreenhouse.api.common;

import com.smartgreenhouse.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传超限不再返回 500，而是 400 + 可读提示。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsBadRequestForOversizeUpload() {
        ResponseEntity<ApiResponse<Void>> response = handler
                .handleMaxUploadSize(new MaxUploadSizeExceededException(
                        20L * 1024 * 1024
                ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("40000");
        assertThat(response.getBody().message()).contains("20 MB");
    }

    @Test
    void detectsOversizeUploadWrappedInContainerException() {
        Exception wrapped = new IllegalStateException(
                "容器解析 multipart 失败",
                new MaxUploadSizeExceededException(20L * 1024 * 1024)
        );

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpectedException(wrapped);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("40000");
    }
}
