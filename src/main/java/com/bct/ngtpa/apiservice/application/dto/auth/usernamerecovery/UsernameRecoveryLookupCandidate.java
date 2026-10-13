package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

/**
 * Minimal pre-schemeRef candidate record crossing the Username Recovery lookup boundary.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-apim-integration-design.md} section 4/5/7
 * ({@code env} maps to {@code accountEnv}, {@code trust-code} to {@code trustCode}, {@code
 * isRegistered} to {@code registered}, and {@code cust-id} to {@code userName}; a registered record
 * requires a nonblank {@code cust-id}, an unregistered record must be blank or absent) and {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 3 (minimal
 * candidate fields; policy, certificate, scheme, termination, DOB, and raw identity data are not
 * retained).
 *
 * <p>{@code schemeName}/{@code schemeNameChi} are additive fields sourced from the APIM {@code
 * sch-name}/{@code chin-sch-name} response fields (see
 * docs/design/10-template-service/template-service-solution-design.md section 8.2), carried through
 * so the centralized Template Service can render the {schemeName}/{schemeNameChi} placeholders
 * required by notice content. Optional (nullable), since not every lookup source populates them.
 *
 * <p>Invariants: {@code accountEnv} and {@code trustCode} are required and nonblank; {@code
 * userName} is present and nonblank only for registered candidates. Opaque Scheme-Refs are created
 * only when a session is created and are therefore absent at this boundary. {@link #toString()}
 * never discloses {@code userName}, {@code schemeName}, or {@code schemeNameChi}.
 */
public record UsernameRecoveryLookupCandidate(
    String accountEnv,
    String trustCode,
    boolean registered,
    String userName,
    String schemeName,
    String schemeNameChi) {

  private static final String REDACTED = "<redacted>";

  public UsernameRecoveryLookupCandidate {
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(trustCode, "trustCode must not be null");
    if (accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    if (trustCode.isBlank()) {
      throw new IllegalArgumentException("trustCode must not be blank");
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
    return "UsernameRecoveryLookupCandidate[accountEnv="
        + accountEnv
        + ", trustCode="
        + trustCode
        + ", registered="
        + registered
        + ", userName="
        + (userName == null ? null : REDACTED)
        + ", schemeName="
        + (schemeName == null ? null : REDACTED)
        + ", schemeNameChi="
        + (schemeNameChi == null ? null : REDACTED)
        + "]";
  }
}
