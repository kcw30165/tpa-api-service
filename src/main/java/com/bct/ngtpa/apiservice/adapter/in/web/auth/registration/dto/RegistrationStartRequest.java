package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record RegistrationStartRequest(
        String identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile,
        String captchaChallengeId) {
}
