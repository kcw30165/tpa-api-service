package com.bct.ngtpa.apiservice.application.dto.auth.registration;

public record RegistrationLookupAccount(
        String email,
        String mobileNumber,
        String accountEnv,
        String trustCode) {
    @Override
    public String toString() {
        return "RegistrationLookupAccount[email=<redacted>, mobileNumber=<redacted>, accountEnv=<redacted>, trustCode=<redacted>]";
    }
}
