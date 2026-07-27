package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Non-blocking UI notification. Do not use this for blocking validation/system
 * errors.
 */
public record ApiMessage(
        String type,
        String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message,
        String target) {

    public static ApiMessage success(String code, String message, String target) {
        return new ApiMessage("SUCCESS", code, message, target);
    }

    public static ApiMessage info(String code, String message, String target) {
        return new ApiMessage("INFO", code, message, target);
    }

    public static ApiMessage warning(String code, String message, String target) {
        return new ApiMessage("WARNING", code, message, target);
    }
}
