package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import java.util.List;

public record SimpleMutationResponse<T>(boolean success, String status, T result, List<String> messages, List<SimpleWebError> errors) {
    public SimpleMutationResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
