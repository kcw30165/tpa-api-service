package com.bct.ngtpa.apiservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when APIM profile resolution fails under strict routing.
 */
public class ApimProfileResolutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ApimProfileResolutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApimProfileResolutionException(String message) {
        super(message);
    }

    public HttpStatus getPublicStatus() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public String getInternalCode() {
        return "err.apim.profile.resolution.failed";
    }
}
