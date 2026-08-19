package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException.Reason;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 020 typed Request rejection signals.
 *
 * <p>Every distinct non-result condition (validation, security context, idempotency in-progress /
 * key reused / unknown outcome existing, invalid/expired session, origin/CSRF failure, candidate
 * not available, revision conflict, definite system failure) is a typed {@link Reason}; the message
 * never contains raw exception text or sensitive values and {@link #toString()} is fully redacted.
 */
class UsernameRecoveryRequestExceptionTest {

  @Test
  void eachFactoryMapsToTheExpectedReason() {
    assertThat(UsernameRecoveryRequestException.validationFailed().reason())
        .isEqualTo(Reason.VALIDATION_FAILED);
    assertThat(UsernameRecoveryRequestException.securityContextRequired().reason())
        .isEqualTo(Reason.SECURITY_CONTEXT_REQUIRED);
    assertThat(UsernameRecoveryRequestException.idempotencyInProgress().reason())
        .isEqualTo(Reason.IDEMPOTENCY_IN_PROGRESS);
    assertThat(UsernameRecoveryRequestException.keyReused().reason())
        .isEqualTo(Reason.IDEMPOTENCY_KEY_REUSED);
    assertThat(UsernameRecoveryRequestException.unknownOutcomeExisting().reason())
        .isEqualTo(Reason.UNKNOWN_OUTCOME_EXISTING);
    assertThat(UsernameRecoveryRequestException.sessionAbsent().reason())
        .isEqualTo(Reason.SESSION_ABSENT);
    assertThat(UsernameRecoveryRequestException.sessionExpired().reason())
        .isEqualTo(Reason.SESSION_EXPIRED);
    assertThat(UsernameRecoveryRequestException.securityInvalid().reason())
        .isEqualTo(Reason.SECURITY_INVALID);
    assertThat(UsernameRecoveryRequestException.candidateNotAvailable().reason())
        .isEqualTo(Reason.CANDIDATE_NOT_AVAILABLE);
    assertThat(UsernameRecoveryRequestException.revisionConflict().reason())
        .isEqualTo(Reason.REVISION_CONFLICT);
    assertThat(UsernameRecoveryRequestException.systemFailure().reason())
        .isEqualTo(Reason.SYSTEM_FAILURE);
  }

  @Test
  void systemFailureCarriesTheCauseForInternalUse() {
    IllegalStateException cause = new IllegalStateException("inner");
    UsernameRecoveryRequestException exception =
        UsernameRecoveryRequestException.systemFailure(cause);

    assertThat(exception.reason()).isEqualTo(Reason.SYSTEM_FAILURE);
    assertThat(exception).hasCause(cause);
  }

  @Test
  void messageNeverContainsRawExceptionText() {
    UsernameRecoveryRequestException exception =
        UsernameRecoveryRequestException.systemFailure(
            new IllegalStateException("sensitive raw detail"));

    assertThat(exception.getMessage()).doesNotContain("sensitive", "raw detail");
  }

  @Test
  void toStringIsRedactedAndNeverDisclosesSensitiveValues() {
    UsernameRecoveryRequestException exception =
        UsernameRecoveryRequestException.systemFailure(
            new IllegalStateException("afs_opaque-1 sch_1 key_12345678"));

    assertThat(exception.toString())
        .isEqualTo("UsernameRecoveryRequestException[reason=SYSTEM_FAILURE]")
        .doesNotContain("afs_opaque-1", "sch_1", "key_12345678");
  }

  @Test
  void reasonAccessorIsAvailableForWebMapping() {
    assertThat(UsernameRecoveryRequestException.sessionAbsent().reason().name())
        .isEqualTo("SESSION_ABSENT");
  }
}
