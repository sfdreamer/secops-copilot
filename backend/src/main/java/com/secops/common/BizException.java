package com.secops.common;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object details;

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BizException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
