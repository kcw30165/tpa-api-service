package com.bct.ngtpa.apiservice.adapter.in.web.response;

import org.springframework.http.HttpStatus;

/**
 * Minimal typed override for approved transport outcomes where the normalized
 * public {@link ApiStatus} alone is insufficient to select the HTTP status.
 *
 * <p>
 * Contains exactly {@code CONFLICT}, {@code TOO_MANY_REQUESTS}, and
 * {@code FORBIDDEN}. It carries no error-code strings and no
 * feature-specific metadata.
 */
public enum HttpStatusClassificationOverride {
    CONFLICT(HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    FORBIDDEN(HttpStatus.FORBIDDEN);

    private final HttpStatus httpStatus;

    HttpStatusClassificationOverride(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    /** Typed accessor for the HTTP status represented by this override. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
