package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.Objects;

/**
 * FE-safe scheme projection for Username Recovery.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 2 (FE receives only
 * {@code schemeRef}, {@code accountEnv}, {@code trustCode}, and {@code isRegistered}) and {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 * (session-bound opaque schemeRefs, including for unregistered schemes).
 *
 * <p>Invariants: {@code accountEnv} and {@code trustCode} are required and nonblank; a registered
 * projection always carries a non-null opaque {@link UsernameRecoverySchemeRef}; an unregistered
 * projection may be constructed without one, but a session-created Start result rejects it. {@link
 * #toString()} never discloses the opaque schemeRef.
 */
public record UsernameRecoverySchemeProjection(
    UsernameRecoverySchemeRef schemeRef, String accountEnv, String trustCode, boolean registered) {

  public UsernameRecoverySchemeProjection {
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(trustCode, "trustCode must not be null");
    if (accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    if (trustCode.isBlank()) {
      throw new IllegalArgumentException("trustCode must not be blank");
    }
    if (registered && schemeRef == null) {
      throw new IllegalArgumentException("registered projection requires a schemeRef");
    }
  }

  @Override
  public String toString() {
    return "UsernameRecoverySchemeProjection[schemeRef="
        + (schemeRef == null ? null : "<redacted>")
        + ", accountEnv="
        + accountEnv
        + ", trustCode="
        + trustCode
        + ", registered="
        + registered
        + "]";
  }
}
