package com.bct.ngtpa.apiservice.domain.model;

import java.util.Objects;

/**
 * Minimal protected Username Recovery candidate retained for later session and request
 * orchestration.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 *       section 5 memory model record and section 3 candidate fields (Scheme-Ref, account
 *       environment, trust code, registration flag, username only when registered);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       section 7 (username for registered candidates only) and section 6 step 11
 *       (opaque Scheme-Refs created only when a session is created);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 *       section 4 (zero-registered projection carries {@code "schemeRef": null});</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md}
 *       APIM invariants (registered requires username; unregistered must not carry one).</li>
 * </ul>
 *
 * <p>Invariants: {@code accountEnv}, {@code trustCode}, and {@code deliveryState} are required
 * (accountEnv/trustCode additionally nonblank per the APIM contract; {@code deliveryState}
 * nonnull); {@code schemeRef} is a non-null {@link UsernameRecoverySchemeRef} for registered
 * candidates and may be null only for pre-session unregistered projections (any non-null
 * Scheme-Ref is nonblank by value-object construction); {@code userName} is present and
 * nonblank only for registered candidates. {@link #toString()} never discloses {@code schemeRef}
 * or {@code userName}.
 */
public record UsernameRecoveryCandidate(
        UsernameRecoverySchemeRef schemeRef,
        String accountEnv,
        String trustCode,
        boolean registered,
        String userName,
        UsernameRecoveryDeliveryState deliveryState) {

    public UsernameRecoveryCandidate {
        Objects.requireNonNull(accountEnv, "accountEnv must not be null");
        Objects.requireNonNull(trustCode, "trustCode must not be null");
        Objects.requireNonNull(deliveryState, "deliveryState must not be null");
        if (accountEnv.isBlank()) {
            throw new IllegalArgumentException("accountEnv must not be blank");
        }
        if (trustCode.isBlank()) {
            throw new IllegalArgumentException("trustCode must not be blank");
        }
        if (registered && schemeRef == null) {
            throw new IllegalArgumentException("registered candidate requires a nonblank schemeRef");
        }
        if (registered && (userName == null || userName.isBlank())) {
            throw new IllegalArgumentException("registered candidate requires a nonblank userName");
        }
        if (!registered && userName != null) {
            throw new IllegalArgumentException("unregistered candidate must not retain a userName");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryCandidate[schemeRef=" + (schemeRef == null ? null : "<redacted>")
                + ", accountEnv=" + accountEnv
                + ", trustCode=" + trustCode
                + ", registered=" + registered
                + ", userName=" + (userName == null ? null : "<redacted>")
                + ", deliveryState=" + deliveryState + "]";
    }
}
