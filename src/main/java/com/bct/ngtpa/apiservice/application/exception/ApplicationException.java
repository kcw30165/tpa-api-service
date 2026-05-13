package com.bct.ngtpa.apiservice.application.exception;

import java.util.Objects;

/**
 * Framework-free base exception for application-level business errors.
 *
 * <p>The {@code errorCode} is the stable public contract value. Any exception message carried by
 * this type is for internal diagnostics only; the user-facing API message is resolved separately.
 */
public class ApplicationException extends RuntimeException {

    private final String errorCode;

    public ApplicationException(String errorCode) {
        super();
        this.errorCode = requireErrorCode(errorCode);
    }

    public ApplicationException(String errorCode, String diagnosticMessage) {
        super(diagnosticMessage);
        this.errorCode = requireErrorCode(errorCode);
    }

    public ApplicationException(String errorCode, Throwable cause) {
        super(cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    public ApplicationException(String errorCode, String diagnosticMessage, Throwable cause) {
        super(diagnosticMessage, cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String requireErrorCode(String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        var trimmed = errorCode.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        return trimmed;
    }
}