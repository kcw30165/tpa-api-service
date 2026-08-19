package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import java.util.Objects;

/**
 * Typed Task 021 cancel rejection signal for every distinct non-result condition of the Username
 * Recovery cancel flow.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} sections 7-8
 * (UR-CANCEL-001..003 HTTP/status mapping) and section 9 (no silent exceptions; unexpected defects
 * reach the central exception boundary). The committed inbound port returns only {@code
 * Mono<UsernameRecoveryCancelResult>}; conditions the result cannot represent - validation failure,
 * a missing security context, invalid CSRF/origin, idempotency in-progress / key-reused /
 * unknown-outcome-existing, revision/concurrency conflict, and definite system failure - are
 * expressed as typed {@link Reason} signals the future web layer can map distinctly.
 *
 * <p>Invariants: {@code reason} is required and the message never contains raw exception text,
 * session ids, CSRF values, destinations, usernames, idempotency keys, fingerprints, or operation
 * references. {@link #toString()} is fully redacted.
 */
public final class UsernameRecoveryCancelException extends RuntimeException {

  private final Reason reason;

  public enum Reason {
    VALIDATION_FAILED,
    SECURITY_CONTEXT_REQUIRED,
    SECURITY_INVALID,
    IDEMPOTENCY_IN_PROGRESS,
    IDEMPOTENCY_KEY_REUSED,
    UNKNOWN_OUTCOME_EXISTING,
    REVISION_CONFLICT,
    SYSTEM_FAILURE
  }

  private UsernameRecoveryCancelException(Reason reason, Throwable cause) {
    super("Username recovery cancel rejected: " + Objects.requireNonNull(reason).name(), cause);
    this.reason = reason;
  }

  public static UsernameRecoveryCancelException validationFailed() {
    return new UsernameRecoveryCancelException(Reason.VALIDATION_FAILED, null);
  }

  public static UsernameRecoveryCancelException securityContextRequired() {
    return new UsernameRecoveryCancelException(Reason.SECURITY_CONTEXT_REQUIRED, null);
  }

  public static UsernameRecoveryCancelException securityInvalid() {
    return new UsernameRecoveryCancelException(Reason.SECURITY_INVALID, null);
  }

  public static UsernameRecoveryCancelException idempotencyInProgress() {
    return new UsernameRecoveryCancelException(Reason.IDEMPOTENCY_IN_PROGRESS, null);
  }

  public static UsernameRecoveryCancelException keyReused() {
    return new UsernameRecoveryCancelException(Reason.IDEMPOTENCY_KEY_REUSED, null);
  }

  public static UsernameRecoveryCancelException unknownOutcomeExisting() {
    return new UsernameRecoveryCancelException(Reason.UNKNOWN_OUTCOME_EXISTING, null);
  }

  public static UsernameRecoveryCancelException revisionConflict() {
    return new UsernameRecoveryCancelException(Reason.REVISION_CONFLICT, null);
  }

  public static UsernameRecoveryCancelException systemFailure() {
    return new UsernameRecoveryCancelException(Reason.SYSTEM_FAILURE, null);
  }

  public static UsernameRecoveryCancelException systemFailure(Throwable cause) {
    return new UsernameRecoveryCancelException(
        Reason.SYSTEM_FAILURE, Objects.requireNonNull(cause, "cause must not be null"));
  }

  public Reason reason() {
    return reason;
  }

  @Override
  public String toString() {
    return "UsernameRecoveryCancelException[reason=" + reason + "]";
  }
}
