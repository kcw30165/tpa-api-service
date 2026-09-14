package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.Objects;

/**
 * Application-level input for the Username Recovery request flow.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 6 (the request
 * is bound to the {@code auth_flow_sid} session) and {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 5 (request
 * targets a single session-bound opaque Scheme-Ref; the {@code Idempotency-Key} header is
 * required).
 *
 * <p>Repair 018A: the raw client-provided {@code Idempotency-Key} enters through this typed
 * command. {@code idempotencyKey} is the opaque client key, distinct from the semantic fingerprint;
 * later use cases may convert it into {@code the repository-wide shared idempotency operation
 * identity}. The Scheme-Ref keeps its committed typed {@link UsernameRecoverySchemeRef}
 * representation. No raw CSRF is carried.
 *
 * <p>Invariants: {@code authFlowSessionId}, {@code schemeRef}, and {@code idempotencyKey} are
 * required; the idempotency key is trimmed and nonblank (its case is preserved). {@link
 * #toString()} never discloses the session id, the opaque Scheme-Ref, or the idempotency key.
 */
public record UsernameRecoveryRequestCommand(
    AuthFlowSessionId authFlowSessionId,
    UsernameRecoverySchemeRef schemeRef,
    String idempotencyKey) {

  public UsernameRecoveryRequestCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(schemeRef, "schemeRef must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    idempotencyKey = idempotencyKey.trim();
    if (idempotencyKey.isBlank()) {
      throw new IllegalArgumentException(
          "username recovery request command idempotencyKey must not be blank");
    }
  }

  @Override
  public String toString() {
    return "UsernameRecoveryRequestCommand[authFlowSessionId=<redacted>, schemeRef=<redacted>"
        + ", idempotencyKey=<redacted>]";
  }
}
