package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record RegistrationSuccessPageResponse(
        String pageId,
        String variant,
        List<RegistrationSuccessPageActionResponse> actions) {
    public RegistrationSuccessPageResponse {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
