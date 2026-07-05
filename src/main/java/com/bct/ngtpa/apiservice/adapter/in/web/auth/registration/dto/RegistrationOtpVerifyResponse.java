package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import java.util.List;

public record RegistrationOtpVerifyResponse(
        List<RegistrationUnregisteredTrustResponse> unregisteredTrusts,
        Integer remainingVerifyAttempts) {
    public RegistrationOtpVerifyResponse {
        unregisteredTrusts = unregisteredTrusts == null ? List.of() : List.copyOf(unregisteredTrusts);
    }
    @Override
    public String toString() {
        return "RegistrationOtpVerifyResponse[trustCount=" + unregisteredTrusts.size()
                + ", remainingVerifyAttempts=" + remainingVerifyAttempts + "]";
    }
}
