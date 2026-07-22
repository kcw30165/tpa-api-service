package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record CancelRegistrationResponse(
                java.util.List<RegistrationActionResponse> actions) {
        public CancelRegistrationResponse {
                actions = actions == null ? java.util.List.of() : java.util.List.copyOf(actions);
        }
}
