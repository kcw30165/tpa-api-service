package com.bct.ngtpa.apiservice.application.dto.auth.login;

import java.util.List;

public record CredentialAuthenticationResult(
        CredentialAuthenticationStatus status,
        LoginActorReference actorReference,
        List<OtpDestination> otpDestinations,
        String reasonCode) {

    @Override
    public List<OtpDestination> otpDestinations() {
        return otpDestinations == null ? List.of() : List.copyOf(otpDestinations);
    }
}
