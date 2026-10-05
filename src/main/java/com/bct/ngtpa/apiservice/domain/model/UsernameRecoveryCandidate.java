package com.bct.ngtpa.apiservice.domain.model;

import java.util.Objects;

/**
 * Minimal protected Username Recovery candidate retained for later session and request
 * orchestration.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section
 *       5 memory model record and section 3 candidate fields (Scheme-Ref, account environment,
 *       trust code, registration flag, username only when registered);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7
 *       (username for registered candidates only) and section 6 step 11 (opaque Scheme-Refs
 *       created only when a session is created);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 *       (zero-registered projection carries {@code "schemeRef": null});
 *   <li>{@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md} APIM
 *       invariants (registered requires username; unregistered must not carry one).
 * </ul>
 *
 * <p>{@code schemeName}/{@code schemeNameChi} are additive fields carried from {@link
 * com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate}
 * so the centralized Template Service can render the {schemeName}/{schemeNameChi} placeholders
 * (see docs/design/10-template-service/template-service-solution-design.md section 8.2). Optional
 * (nullable).
 *
 * <p>Invariants: {@code accountEnv}, {@code trustCode}, and {@code deliveryState} are required
 * (accountEnv/trustCode additionally nonblank per the APIM contract; {@code deliveryState}
 * nonnull); {@code schemeRef} is a non-null {@link UsernameRecoverySchemeRef} for registered
 * candidates and may be null only for pre-session unregistered projections (any non-null
 * Scheme-Ref is nonblank by value-object construction); {@code userName} is present and nonblank
 * only for registered candidates. {@link #toString()} never discloses {@code schemeRef}, {@code
 * userName}, {@code schemeName}, or {@code schemeNameChi}.
 */
public record UsernameRecoveryCandidate(
    UsernameRecoverySchemeRef schemeRef,
    String accountEnv,
    String trustCode,
    boolean registered,
    String userName,
    UsernameRecoveryDeliveryState deliveryState,
    String schemeName,
    String schemeNameChi) {

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

  /**
   * Compatibility constructor for existing call sites predating the addition of {@code
   * schemeName}/{@code schemeNameChi}. Both new fields default to {@code null}.
   *
   * <p><b>Caution:</b> any caller rebuilding a candidate from an existing one (e.g. a withX-style
   * state transition) should use the canonical 8-arg constructor and forward {@code
   * schemeName()}/{@code schemeNameChi()} explicitly, not this compatibility constructor, or the
   * values will be silently nulled out.
   */
  public UsernameRecoveryCandidate(
      UsernameRecoverySchemeRef schemeRef,
      String accountEnv,
      String trustCode,
      boolean registered,
      String userName,
      UsernameRecoveryDeliveryState deliveryState) {
    this(schemeRef, accountEnv, trustCode, registered, userName, deliveryState, null, null);
  }

  @Override
  public String toString() {
    return "UsernameRecoveryCandidate[schemeRef="
        + (schemeRef == null ? null : "<redacted>")
        + ", accountEnv="
        + accountEnv
        + ", trustCode="
        + trustCode
        + ", registered="
        + registered
        + ", userName="
        + (userName == null ? null : "<redacted>")
        + ", deliveryState="
        + deliveryState
        + "]";
  }
}
