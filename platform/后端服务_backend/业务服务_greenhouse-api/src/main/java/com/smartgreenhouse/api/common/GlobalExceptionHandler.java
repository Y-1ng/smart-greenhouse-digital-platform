package com.smartgreenhouse.api.common;

import com.smartgreenhouse.common.api.ApiResponse;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 将业务异常和参数校验异常转换为统一响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(statusFor(exception.getCode()))
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException exception
    ) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(
                        errorCode.code(),
                        errorCode.message()
                ));
    }

    /**
     * 上传文件超过大小限制：返回 400 与可读提示，不打印整段堆栈。
     * Tomcat 在解析 multipart 时会抛出内部异常（FileSizeLimitExceededException），
     * 因此这里按异常链判断，避免直接依赖容器内部类型。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception
    ) {
        log.warn("上传文件超过大小限制：{}", exception.getMessage());
        return uploadTooLargeResponse();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        if (isUploadTooLarge(exception)) {
            log.warn("上传文件超过大小限制：{}", exception.getMessage());
            return uploadTooLargeResponse();
        }
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        log.error("Unexpected API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(errorCode.code(), errorCode.message()));
    }

    private boolean isUploadTooLarge(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MaxUploadSizeExceededException
                    || current.getClass().getSimpleName()
                    .contains("FileSizeLimitExceeded")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ResponseEntity<ApiResponse<Void>> uploadTooLargeResponse() {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ErrorCode.BAD_REQUEST.code(),
                "上传文件超过 20 MB 限制，请压缩后重试"
        ));
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "40100" -> HttpStatus.UNAUTHORIZED;
            case "40300" -> HttpStatus.FORBIDDEN;
            case "40400" -> HttpStatus.NOT_FOUND;
            case "40900" -> HttpStatus.CONFLICT;
            case "50301", "50302" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "50000" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
