package com.secops.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * 统一响应包装，结构以 API.md 1.2 / 1.3 为准。
 * 成功响应不含 error 字段；失败响应 data 固定为 null。
 */
@JsonInclude(Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        @JsonInclude(Include.ALWAYS) T data,
        ErrorBody error
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> fail(String message, ErrorCode code) {
        return fail(message, code, null);
    }

    public static <T> ApiResponse<T> fail(String message, ErrorCode code, Object details) {
        return new ApiResponse<>(false, message, null, new ErrorBody(code.name(), details));
    }

    @JsonInclude(Include.NON_NULL)
    public record ErrorBody(
            String code,
            @JsonInclude(Include.ALWAYS) Object details
    ) {
    }
}
