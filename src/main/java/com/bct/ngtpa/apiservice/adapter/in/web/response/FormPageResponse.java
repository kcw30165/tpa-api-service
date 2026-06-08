package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record FormPageResponse<TForm>(
        boolean success,
        ApiStatus status,
        PageResponse page,
        TForm form,
        List<ApiMessage> messages,
        List<ApiError> errors
) implements ApiResponse {

    public FormPageResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static <TForm> FormPageResponse<TForm> success(
            PageResponse page,
            TForm form) {

        return new FormPageResponse<>(
                true,
                ApiStatus.SUCCESS,
                page,
                form,
                List.of(),
                List.of()
        );
    }
}