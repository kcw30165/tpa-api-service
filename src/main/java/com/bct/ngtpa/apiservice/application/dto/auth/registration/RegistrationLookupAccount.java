package com.bct.ngtpa.apiservice.application.dto.auth.registration;

public record RegistrationLookupAccount(
        String email,
        String mobileNumber,
        String policyNo,
        String certNo,
        String accountEnv,
        String trustCode) {
    @Override
    public String toString() {
        return "RegistrationLookupAccount[email=<redacted>, mobileNumber=<redacted>, policyNo=<redacted>, certNo=<redacted>, accountEnv=<redacted>, trustCode=<redacted>]";
    }
}
