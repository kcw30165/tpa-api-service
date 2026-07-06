package com.bct.ngtpa.apiservice.application.dto.auth.registration;

public record RegistrationStartInput(
        String identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile,
        String captchaChallengeId) {
    @Override
    public String toString() {
        return "RegistrationStartInput[identityType=" + identityType
                + ", identityValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>, captchaChallengeId=<redacted>]";
    }
}
