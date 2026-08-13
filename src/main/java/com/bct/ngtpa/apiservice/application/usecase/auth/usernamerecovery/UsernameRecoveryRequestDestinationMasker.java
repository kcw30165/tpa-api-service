package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import java.util.Objects;

/**
 * Masks the original verified Username Recovery destination for FE display only.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 5 (request result {@code maskedDestination} example {@code p****c@ah.ozk}) and
 * {@code docs/design/6-username-recovery/username-recovery-nonprod-destination-override-design.md}
 * section 6 (the public response must never return the destination in full; the request use case
 * masks the original verified value before exposing it; a delivery override is transport-only).
 *
 * <p>Email keeps the first and last local-part characters around {@code ****}; mobile keeps only
 * the last four digits (reusing the shared Login/Forgot Password convention). The raw value never
 * appears in the output. {@link #toString()} of this stateless helper discloses nothing.
 */
public final class UsernameRecoveryRequestDestinationMasker {

    public String mask(UsernameRecoveryVerifiedDestination.Channel channel, String destination) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        if (destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        return switch (channel) {
            case EMAIL -> maskEmail(destination);
            case MOBILE -> maskMobile(destination);
        };
    }

    private static String maskEmail(String destination) {
        int at = destination.indexOf('@');
        if (at <= 0) {
            throw new IllegalArgumentException("invalid email destination");
        }
        String local = destination.substring(0, at);
        String domain = destination.substring(at);
        if (local.length() == 1) {
            return local + "****" + domain;
        }
        return local.substring(0, 1) + "****" + local.substring(local.length() - 1) + domain;
    }

    private static String maskMobile(String destination) {
        if (destination.length() < 4) {
            throw new IllegalArgumentException("invalid mobile destination");
        }
        return "*".repeat(destination.length() - 4) + destination.substring(destination.length() - 4);
    }
}
