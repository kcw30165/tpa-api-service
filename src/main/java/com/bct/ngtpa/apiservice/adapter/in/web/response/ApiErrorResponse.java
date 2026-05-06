package com.bct.ngtpa.apiservice.adapter.in.web.response;

public record ApiErrorResponse(String errorCode, String message) {

    public static ApiErrorResponse of(String errorCode, String message) {
        return new ApiErrorResponse(errorCode, message);
    }
}
