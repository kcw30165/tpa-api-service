package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Branch-oriented tests for {@link ForgotPasswordOtpResendFoundationService}. */
class ForgotPasswordOtpResendFoundationServiceTest {

  private final ForgotPasswordOtpResendEligibilityValidator validator =
      mock(ForgotPasswordOtpResendEligibilityValidator.class);

  private ForgotPasswordOtpResendFoundationService newService() {
    return new ForgotPasswordOtpResendFoundationService(validator);
  }

  @Test
  void constructorRejectsNullValidator() {
    assertThatThrownBy(() -> new ForgotPasswordOtpResendFoundationService(null))
        .isInstanceOf(NullPointerException.class);
  }

  private ForgotPasswordOtpResendCommand command(SessionLockHandle lock, AuthFlowSession session) {
    return new ForgotPasswordOtpResendCommand(
        lock,
        session,
        mock(OtpChallenge.class),
        ForgotPasswordProtectedPhase.OTP_SENT,
        0,
        "idem-1",
        Instant.parse("2026-09-14T00:00:00Z"));
  }

  @Test
  void nullCommandYieldsSessionInvalid() {
    StepVerifier.create(newService().evaluate(null))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void nullMutationLockYieldsSessionInvalid() {
    StepVerifier.create(newService().evaluate(command(null, mock(AuthFlowSession.class))))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void unacquiredMutationLockYieldsSessionInvalid() {
    SessionLockHandle unacquired = new SessionLockHandle(false, null, null);
    StepVerifier.create(newService().evaluate(command(unacquired, mock(AuthFlowSession.class))))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void nullLockReloadedSessionYieldsSessionInvalid() {
    SessionLockHandle acquired = new SessionLockHandle(true, "lock-key", "lock-token");
    StepVerifier.create(newService().evaluate(command(acquired, null)))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void validCommandDelegatesToValidatorAndMapsResult() {
    SessionLockHandle acquired = new SessionLockHandle(true, "lock-key", "lock-token");
    AuthFlowSessionId sid = new AuthFlowSessionId("afs-1");
    AuthFlowSession session = mock(AuthFlowSession.class);
    when(session.authFlowSessionId()).thenReturn(sid);
    when(session.status())
        .thenReturn(
            com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.OTP_SENT);

    ForgotPasswordOtpResendEligibilityResult validatorResult =
        new ForgotPasswordOtpResendEligibilityResult(
            ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE, null, 0, 2);
    when(validator.validate(any())).thenReturn(validatorResult);

    StepVerifier.create(newService().evaluate(command(acquired, session)))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE);
              assertThat(r.successfulResendCount()).isEqualTo(2);
            })
        .verifyComplete();
  }
}
