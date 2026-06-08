package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

/**
 * Blocking validation/business/system error. Validation messages belong here, not in messages[].
 */
public record ApiError(
        String type,
        String code,
        String message,
        List<String> targets,
        String severity,
        String source) {

    public ApiError {
        targets = targets == null ? List.of() : List.copyOf(targets);
        severity = severity == null || severity.isBlank() ? "ERROR" : severity;
    }

    public static ApiError field(String code, String message, List<String> targets, String source) {
        return new ApiError("FIELD", code, message, targets, "ERROR", source);
    }

    public static ApiError crossField(String code, String message, List<String> targets, String source) {
        return new ApiError("CROSS_FIELD", code, message, targets, "ERROR", source);
    }

    public static ApiError form(String code, String message, List<String> targets, String source) {
        return new ApiError("FORM", code, message, targets, "ERROR", source);
    }

    public static ApiError business(String code, String message, List<String> targets, String source) {
        return new ApiError("BUSINESS", code, message, targets, "ERROR", source);
    }

    public static ApiError system(String code, String message, String source) {
        return new ApiError("SYSTEM", code, message, List.of(), "ERROR", source);
    }
}
