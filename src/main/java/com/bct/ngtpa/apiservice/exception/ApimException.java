package com.bct.ngtpa.apiservice.exception;

import org.springframework.http.HttpStatusCode;

public class ApimException extends RuntimeException {
    private final HttpStatusCode statusCode;

    public ApimException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
