package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import java.util.Objects;

/**
 * Application-level input for Username Recovery effective destination resolution.
 *
 * <p>Carries the session-resident verified destination and channel exactly as persisted in the
 * protected session; resolution never mutates it (the non-production override changes only the
 * delivery recipient, never the verified session value).
 *
 * <p>Design references:
 * {@code docs/design/6-username-recovery/username-recovery-nonprod-destination-override-design.md}
 * section 5 (verifiedDestination is retained in protected session context and never replaced) and
 * section 6 (the effective destination is resolved immediately before delivery).
 *
 * <p>Invariants: {@code verifiedDestination} is required. {@link #toString()} never discloses the
 * destination value.
 */
public record UsernameRecoveryEffectiveDestinationCommand(
        UsernameRecoveryVerifiedDestination verifiedDestination) {

    public UsernameRecoveryEffectiveDestinationCommand {
        Objects.requireNonNull(verifiedDestination, "verifiedDestination must not be null");
    }

    @Override
    public String toString() {
        return "UsernameRecoveryEffectiveDestinationCommand[channel="
                + verifiedDestination.channel() + ", verifiedDestination=<redacted>]";
    }
}
