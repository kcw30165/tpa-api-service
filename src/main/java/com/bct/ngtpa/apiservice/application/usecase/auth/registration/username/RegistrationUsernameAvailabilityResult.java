package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

public record RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus status,
        String downstreamErrorCode) {
}
