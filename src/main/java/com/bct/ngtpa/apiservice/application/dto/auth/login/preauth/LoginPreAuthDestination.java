package com.bct.ngtpa.apiservice.application.dto.auth.login.preauth;

import java.util.Objects;

public record LoginPreAuthDestination(
        String destinationId,
        String channel,
        String destination,
        String maskedDestination) {
    public LoginPreAuthDestination {
        Objects.requireNonNull(destinationId);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(maskedDestination);
    }

    @Override
    public String toString() {
        return "LoginPreAuthDestination[destinationId=<redacted>, channel=" + channel
                + ", destination=<redacted>, maskedDestination=<redacted>]";
    }
}
