package com.bct.ngtpa.apiservice.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.springframework.http.HttpStatusCode;

import java.util.Objects;

/**
 * Runtime exception for Config Service outbound adapter failures.
 * Mirrors {@link ApimException} in shape so the web exception handler can be extended
 * with a consistent pattern.
 */
public class ConfigServiceException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String errorCode;

    public ConfigServiceException(HttpStatusCode statusCode, String message) {
        this(statusCode, defaultErrorCode(statusCode), message, null);
    }

    public ConfigServiceException(HttpStatusCode statusCode, String message, Throwable cause) {
        this(statusCode, defaultErrorCode(statusCode), message, cause);
    }

    public ConfigServiceException(HttpStatusCode statusCode, String errorCode, String message) {
        this(statusCode, errorCode, message, null);
    }

    public ConfigServiceException(HttpStatusCode statusCode, String errorCode, String message, Throwable cause) {
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
        if (trimmed.isEmpty() || trimmed.chars().allMatch(Character::isDigit)) {
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
