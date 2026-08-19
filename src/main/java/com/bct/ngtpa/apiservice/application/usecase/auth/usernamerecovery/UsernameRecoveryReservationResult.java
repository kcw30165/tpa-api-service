package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import java.util.Objects;

/**
 * Minimal typed result of the Username Recovery candidate reservation boundary.
 *
 * <p>The result deliberately carries no protected session, session identifier, Scheme-Ref, CSRF
 * value, username, or destination. A successful reservation is represented only by the typed {@link
 * UsernameRecoveryReservationOutcome#RESERVED} outcome. Later orchestration must use its own
 * authorized boundary rather than extracting protected state from this result.
 */
public record UsernameRecoveryReservationResult(UsernameRecoveryReservationOutcome outcome) {

  public UsernameRecoveryReservationResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
  }

  public static UsernameRecoveryReservationResult reserved() {
    return new UsernameRecoveryReservationResult(UsernameRecoveryReservationOutcome.RESERVED);
  }

  public static UsernameRecoveryReservationResult absent() {
    return new UsernameRecoveryReservationResult(UsernameRecoveryReservationOutcome.SESSION_ABSENT);
  }

  public static UsernameRecoveryReservationResult expired() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.SESSION_EXPIRED);
  }

  public static UsernameRecoveryReservationResult csrfInvalid() {
    return new UsernameRecoveryReservationResult(UsernameRecoveryReservationOutcome.CSRF_INVALID);
  }

  public static UsernameRecoveryReservationResult schemeRefInvalid() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.SCHEME_REF_INVALID);
  }

  public static UsernameRecoveryReservationResult registrationRequired() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.REGISTRATION_REQUIRED);
  }

  public static UsernameRecoveryReservationResult alreadyRequested() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.ALREADY_REQUESTED);
  }

  public static UsernameRecoveryReservationResult candidateNotAvailable() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.CANDIDATE_NOT_AVAILABLE);
  }

  public static UsernameRecoveryReservationResult terminal() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL);
  }

  public static UsernameRecoveryReservationResult revisionConflict() {
    return new UsernameRecoveryReservationResult(
        UsernameRecoveryReservationOutcome.REVISION_CONFLICT);
  }

  public static UsernameRecoveryReservationResult systemFailed() {
    return new UsernameRecoveryReservationResult(UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
  }
}
