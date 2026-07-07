package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public record RegistrationUnregisteredTrust(String accountEnv, String policyNo, String certNo, String trustCode) {
    @Override
    public String toString() {
        return "RegistrationUnregisteredTrust[accountEnv=" + accountEnv
                + ", policyNo=<redacted>, certNo=<redacted>, trustCode=" + trustCode + "]";
    }
}
