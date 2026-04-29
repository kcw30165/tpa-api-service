package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.time.Instant;

public record ApiErrorResponse(String errorCode, String message, String timestamp) {

    public static ApiErrorResponse of(String errorCode, String message) {
        return new ApiErrorResponse(errorCode, message, Instant.now().toString());
    }
}
