package com.bct.ngtpa.apiservice.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.springframework.http.HttpStatusCode;

import java.util.Objects;

public class ApimException extends RuntimeException {
    private final HttpStatusCode statusCode;
    private final String errorCode;

    public ApimException(HttpStatusCode statusCode, String message) {
        this(statusCode, defaultErrorCode(statusCode), message, null);
    }

    public ApimException(HttpStatusCode statusCode, String message, Throwable cause) {
        this(statusCode, defaultErrorCode(statusCode), message, cause);
    }

    public ApimException(HttpStatusCode statusCode, String errorCode, String message) {
        this(statusCode, errorCode, message, null);
    }

    public ApimException(HttpStatusCode statusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode must not be null");
        this.errorCode = normalizeErrorCode(statusCode, errorCode);
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String normalizeErrorCode(HttpStatusCode statusCode, String errorCode) {
        if (errorCode == null) {
            return defaultErrorCode(statusCode);
        }

        var trimmed = errorCode.trim();
        if (trimmed.isEmpty()) {
            return defaultErrorCode(statusCode);
        }
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return defaultErrorCode(statusCode);
        }
        return trimmed;
    }

    private static String defaultErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 503 -> ErrorCodes.APIM_SERVICE_UNAVAILABLE;
            case 504 -> ErrorCodes.APIM_TIMEOUT;
            case 502 -> ErrorCodes.APIM_UPSTREAM_FAILURE;
            default -> ErrorCodes.SYSTEM_UNEXPECTED;
        };
    }
}
