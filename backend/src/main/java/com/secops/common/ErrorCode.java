package com.secops.common;

/** HTTP 状态码与错误码，以 API.md 1.4 为准。 */
public enum ErrorCode {

    INVALID_ARGUMENT(400),
    VALIDATION_ERROR(400),
    ALERT_NOT_FOUND(404),
    INCIDENT_NOT_FOUND(404),
    ANALYSIS_IN_PROGRESS(409),
    INVALID_INCIDENT_STATUS(409),
    STRATEGY_NOT_SUPPORTED(422),
    INTERNAL_ERROR(500),
    FIXTURE_LOAD_ERROR(500),
    MODEL_SERVICE_ERROR(503),
    AGENT_EXECUTION_ERROR(503),
    AI_OUTPUT_INVALID(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
