package com.bct.ngtpa.apiservice.exception;

import org.springframework.http.HttpStatusCode;

public class ApimException extends RuntimeException {
    private final HttpStatusCode statusCode;
    private final String errorCode;

    public ApimException(HttpStatusCode statusCode, String message) {
        this(statusCode, String.valueOf(statusCode.value()), message);
    }

    public ApimException(HttpStatusCode statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
