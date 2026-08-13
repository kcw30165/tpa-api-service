package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import java.util.Objects;

/**
 * Application result of Username Recovery effective destination resolution.
 *
 * <p>Design references:
 * {@code docs/design/6-username-recovery/username-recovery-nonprod-destination-override-design.md}
 * section 5 (effectiveDeliveryDestination equals the verified destination in PROD/DR and the
 * configured override in every other deployment) and section 6 (the public response must never
 * return the destination in full; the request use case masks this value before exposing it).
 *
 * <p>Invariants: {@code destination} is required and nonblank; {@code channel} is required.
 * {@link #toString()} never discloses the destination value.
 */
public record UsernameRecoveryEffectiveDestinationResult(
        String destination,
        UsernameRecoveryVerifiedDestination.Channel channel) {

    public UsernameRecoveryEffectiveDestinationResult {
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        if (destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryEffectiveDestinationResult[destination=<redacted>, channel="
                + channel + "]";
    }
}
