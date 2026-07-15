package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationCancellationPageResponse;

public record CancelRegistrationResponse(
        boolean cancelled,
        RegistrationCancellationPageResponse page) {
}
