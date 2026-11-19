package com.secops.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 错误响应不得包含 API Key、堆栈、完整 Prompt 或敏感凭据（API.md 1.4）。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.fail(e.getMessage(), e.getErrorCode(), e.getDetails()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception e) {
        log.warn("Request validation failed: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("参数或字段校验失败", ErrorCode.VALIDATION_ERROR));
    }

    /** 未知路径/静态资源缺失不是服务端内部错误，避免误报 500 与 ERROR 日志。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("请求的资源不存在", ErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected server error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.fail("服务内部错误", ErrorCode.INTERNAL_ERROR));
    }
}
