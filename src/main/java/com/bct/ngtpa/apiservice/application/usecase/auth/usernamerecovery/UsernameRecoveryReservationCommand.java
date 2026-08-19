package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.time.Instant;
import java.util.Objects;

/**
 * Application input for the Username Recovery candidate reservation boundary.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}: the canonical session
 * identifier is {@link AuthFlowSessionId}, the sole canonical identifier for both the generic
 * protected auth-flow envelope. The legacy {@code sessionId} component was replaced by {@code
 * authFlowSessionId}.
 *
 * <p>The command exposes only the canonical {@link AuthFlowSessionId}. Migration compatibility
 * constructors and accessors were removed in Repair 025D-1 and must not be reintroduced.
 */
public record UsernameRecoveryReservationCommand(
    AuthFlowSessionId authFlowSessionId, UsernameRecoverySchemeRef schemeRef, Instant now) {

  public UsernameRecoveryReservationCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(schemeRef, "schemeRef must not be null");
    Objects.requireNonNull(now, "now must not be null");
  }

  @Override
  public String toString() {
    return "UsernameRecoveryReservationCommand[authFlowSessionId=<redacted>, schemeRef=<redacted>, now="
        + now
        + "]";
  }
}
