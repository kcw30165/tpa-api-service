package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record MutationResponse<T>(
        boolean success,
        ApiStatus status,
        T result,
        List<ApiMessage> messages,
        List<ApiError> errors) implements ApiResponse {

    public MutationResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static <T> MutationResponse<T> success(ApiStatus status, T result, List<ApiMessage> messages) {
        return new MutationResponse<>(true, status, result, messages, List.of());
    }

    public static <T> MutationResponse<T> failure(ApiStatus status, List<ApiError> errors) {
        return new MutationResponse<>(false, status, null, List.of(), errors);
    }
}
