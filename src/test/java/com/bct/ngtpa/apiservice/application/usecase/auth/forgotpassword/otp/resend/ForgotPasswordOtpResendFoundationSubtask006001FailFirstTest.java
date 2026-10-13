package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpResendFoundationSubtask006001FailFirstTest {
  private final Instant now = Instant.parse("2026-08-01T00:00:00Z");
  private final ForgotPasswordOtpResendPolicy policy = new ForgotPasswordOtpResendPolicy(60, 5);
  private final ForgotPasswordOtpResendEligibilityValidator validator =
      new ForgotPasswordOtpResendEligibilityValidator(policy);

  @Test
  void approvedPolicyAllowsFiveAndRejectsSixth() {
    assertThat(policy.evaluate(now.minusSeconds(60), 4, now).status())
        .isEqualTo(ForgotPasswordOtpResendPolicyStatus.ALLOWED);
    assertThat(policy.evaluate(now.minusSeconds(60), 5, now).status())
        .isEqualTo(ForgotPasswordOtpResendPolicyStatus.RESEND_LIMIT_REACHED);
  }

  @Test
  void cooldownIsMeasuredFromLatestAcceptedIssue() {
    var result = policy.evaluate(now.minusSeconds(30), 0, now);
    assertThat(result.status()).isEqualTo(ForgotPasswordOtpResendPolicyStatus.COOLDOWN_ACTIVE);
    assertThat(result.retryAfterSeconds()).isEqualTo(30);
  }

  @Test
  void otpSentBoundChallengeIsEligibleEvenWhenExpired() {
    var result =
        validator.validate(
            input(ForgotPasswordProtectedPhase.OTP_SENT, challenge(now.minusSeconds(1)), 0));
    assertThat(result.status()).isEqualTo(ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE);
  }

  @Test
  void otpSentSessionInCooldownReturnsCooldownInsteadOfSessionInvalid() {
    var result =
        validator.validate(
            input(ForgotPasswordProtectedPhase.OTP_SENT, challenge(now.plusSeconds(240), now), 1));

    assertThat(result.status()).isEqualTo(ForgotPasswordOtpResendEligibilityStatus.COOLDOWN_ACTIVE);
    assertThat(result.retryAfterSeconds()).isEqualTo(60);
    assertThat(result.successfulResendCount()).isEqualTo(1);
  }

  @Test
  void blockedAndInvalidStatesFailClosed() {
    assertThat(
            validator
                .validate(
                    input(
                        ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN,
                        challenge(now.plusSeconds(300)),
                        0))
                .status())
        .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.UNKNOWN_OUTCOME_BLOCKED);
    assertThat(
            validator
                .validate(
                    input(
                        ForgotPasswordProtectedPhase.IDENTITY_VERIFIED,
                        challenge(now.plusSeconds(300)),
                        0))
                .status())
        .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.INVALID_PHASE);
    assertThat(validator.validate(input(ForgotPasswordProtectedPhase.OTP_SENT, null, 0)).status())
        .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.NO_ACTIVE_CHALLENGE);
  }

  @Test
  void replacementTransitionsDoNotActivateBeforeAccepted() {
    var state = new ForgotPasswordOtpResendState("old", 0, now.minusSeconds(60), false);
    assertThat(
            state
                .transition(ForgotPasswordOtpResendDeliveryOutcome.FAILED, "new", now)
                .activeChallengeId())
        .isEqualTo("old");
    assertThat(
            state
                .transition(ForgotPasswordOtpResendDeliveryOutcome.ACCEPTED, "new", now)
                .successfulResendCount())
        .isEqualTo(1);
    assertThat(
            state
                .transition(ForgotPasswordOtpResendDeliveryOutcome.UNKNOWN_OUTCOME, "new", now)
                .blocked())
        .isTrue();
  }

  private ForgotPasswordOtpResendEligibilityInput input(
      ForgotPasswordProtectedPhase phase, OtpChallenge c, int count) {
    return new ForgotPasswordOtpResendEligibilityInput(
        AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.OTP_SENT, "afs-1", c, phase, count, now);
  }

  private OtpChallenge challenge(Instant expires) {
    return challenge(expires, now.minusSeconds(60));
  }

  private OtpChallenge challenge(Instant expires, Instant lastSentAt) {
    return new OtpChallenge(
        "otp-1",
        OtpScenario.FORGOT_PASSWORD,
        "afs-1",
        OtpChannel.SMS,
        "hash",
        "code",
        OtpStatus.CREATED,
        0,
        5,
        0,
        5,
        now.minusSeconds(300),
        expires,
        lastSentAt,
        null,
        null);
  }
}
