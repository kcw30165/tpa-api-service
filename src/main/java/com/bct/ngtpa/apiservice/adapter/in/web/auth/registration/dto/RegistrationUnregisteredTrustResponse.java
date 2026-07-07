package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record RegistrationUnregisteredTrustResponse(
        String accountEnv,
        String policyNo,
        String certNo,
        String trustCode) {
    @Override
    public String toString() {
        return "RegistrationUnregisteredTrustResponse[accountEnv=" + accountEnv
                + ", policyNo=<redacted>, certNo=<redacted>, trustCode=" + trustCode + "]";
    }
}
