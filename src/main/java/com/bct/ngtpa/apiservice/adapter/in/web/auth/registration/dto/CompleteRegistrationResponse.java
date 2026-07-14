package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageResponse;
import java.util.List;

public record CompleteRegistrationResponse(
        boolean registered,
        List<RegistrationUnregisteredTrustResponse> unregisteredTrusts,
        RegistrationSuccessPageResponse page) {
    public CompleteRegistrationResponse {
        unregisteredTrusts = unregisteredTrusts == null ? List.of() : List.copyOf(unregisteredTrusts);
    }
}
