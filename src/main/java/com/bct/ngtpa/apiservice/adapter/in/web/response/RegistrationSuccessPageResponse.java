package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record RegistrationSuccessPageResponse(
        String pageId,
        String variant,
        String title,
        String body,
        List<RegistrationSuccessPageActionResponse> actions) {
}