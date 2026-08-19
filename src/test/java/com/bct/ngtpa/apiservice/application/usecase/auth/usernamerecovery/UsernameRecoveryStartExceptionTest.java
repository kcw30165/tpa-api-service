package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException.Reason;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the typed Task 019 start rejection signal: every distinct non-result outcome of the
 * Username Recovery start flow (validation, idempotency in-progress/key-reused/unknown outcome,
 * captcha rejection, and definite system failure) is a typed constant that the future web layer can
 * map distinctly. {@link #toString()} never discloses validation field values, raw exception text,
 * identity, DOB, contact, captcha, idempotency-key, or CSRF data.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} sections 7-8
 * (UR-START-001..015 HTTP/status mapping) and section 9 (no silent exceptions; unexpected defects
 * reach the central exception boundary).
 */
class UsernameRecoveryStartExceptionTest {

  @Test
  void carriesDistinctTypedReasonsForEveryNonResultOutcome() {
    assertThat(Reason.VALIDATION_FAILED).isNotEqualTo(Reason.SYSTEM_FAILURE);
    assertThat(Reason.values())
        .containsExactly(
            Reason.VALIDATION_FAILED,
            Reason.IDEMPOTENCY_IN_PROGRESS,
            Reason.IDEMPOTENCY_KEY_REUSED,
            Reason.UNKNOWN_OUTCOME_EXISTING,
            Reason.CAPTCHA_INVALID,
            Reason.CAPTCHA_NOT_FOUND,
            Reason.CAPTCHA_NOT_VERIFIED,
            Reason.CAPTCHA_EXPIRED,
            Reason.CAPTCHA_CONFLICT,
            Reason.REPLAY_REVISION_CONFLICT,
            Reason.SYSTEM_FAILURE);
  }

  @Test
  void validationFailedCarriesTargetedErrors() {
    var errors =
        List.of(
            new UsernameRecoveryStartValidator.Error(
                "email", UsernameRecoveryStartValidator.Kind.EMAIL_INVALID),
            new UsernameRecoveryStartValidator.Error(
                "mobile", UsernameRecoveryStartValidator.Kind.CONTACT_EXACTLY_ONE_REQUIRED));

    UsernameRecoveryStartException exception =
        UsernameRecoveryStartException.validationFailed(errors);

    assertThat(exception.reason()).isEqualTo(Reason.VALIDATION_FAILED);
    assertThat(exception.validationErrors()).containsExactlyElementsOf(errors);
    assertThat(exception.getMessage()).contains("VALIDATION_FAILED").doesNotContain("p****");
  }

  @Test
  void idempotencyConflictsAreDistinctTypedSignals() {
    assertThat(UsernameRecoveryStartException.idempotencyInProgress().reason())
        .isEqualTo(Reason.IDEMPOTENCY_IN_PROGRESS);
    assertThat(UsernameRecoveryStartException.keyReused().reason())
        .isEqualTo(Reason.IDEMPOTENCY_KEY_REUSED);
    assertThat(UsernameRecoveryStartException.unknownOutcomeExisting().reason())
        .isEqualTo(Reason.UNKNOWN_OUTCOME_EXISTING);
  }

  @Test
  void captchaRejectionsAreDistinctTypedSignals() {
    assertThat(UsernameRecoveryStartException.captchaInvalid().reason())
        .isEqualTo(Reason.CAPTCHA_INVALID);
    assertThat(UsernameRecoveryStartException.captchaNotFound().reason())
        .isEqualTo(Reason.CAPTCHA_NOT_FOUND);
    assertThat(UsernameRecoveryStartException.captchaNotVerified().reason())
        .isEqualTo(Reason.CAPTCHA_NOT_VERIFIED);
    assertThat(UsernameRecoveryStartException.captchaExpired().reason())
        .isEqualTo(Reason.CAPTCHA_EXPIRED);
    assertThat(UsernameRecoveryStartException.captchaConflict().reason())
        .isEqualTo(Reason.CAPTCHA_CONFLICT);
  }

  @Test
  void replayRevisionConflictIsDistinctFromSystemFailure() {
    assertThat(UsernameRecoveryStartException.replayRevisionConflict().reason())
        .isEqualTo(Reason.REPLAY_REVISION_CONFLICT)
        .isNotEqualTo(Reason.SYSTEM_FAILURE);
  }

  @Test
  void systemFailurePreservesCauseWithoutDisclosingIt() {
    RuntimeException root = new IllegalStateException("sensitive downstream detail");

    UsernameRecoveryStartException exception = UsernameRecoveryStartException.systemFailure(root);

    assertThat(exception.reason()).isEqualTo(Reason.SYSTEM_FAILURE);
    assertThat(exception.getCause()).isSameAs(root);
    assertThat(exception.getMessage())
        .doesNotContain("sensitive downstream detail")
        .doesNotContain(root.getClass().getName());
  }

  @Test
  void toStringNeverDisclosesFieldValuesOrRawText() {
    var errors =
        List.of(
            new UsernameRecoveryStartValidator.Error(
                "identityValue", UsernameRecoveryStartValidator.Kind.IDENTITY_VALUE_INVALID),
            new UsernameRecoveryStartValidator.Error(
                "dateOfBirth", UsernameRecoveryStartValidator.Kind.DATE_OF_BIRTH_INVALID));
    UsernameRecoveryStartException exception =
        UsernameRecoveryStartException.validationFailed(errors);

    assertThat(exception.toString())
        .contains("VALIDATION_FAILED")
        .contains("validationErrorCount=2")
        .doesNotContain("identityValue", "dateOfBirth");
    assertThat(UsernameRecoveryStartException.systemFailure().toString())
        .contains("SYSTEM_FAILURE")
        .doesNotContain("exception");
  }

  @Test
  void rejectsNullReasonConstructionPaths() {
    assertThatThrownBy(() -> UsernameRecoveryStartException.validationFailed(null))
        .isInstanceOf(NullPointerException.class);
  }
}
