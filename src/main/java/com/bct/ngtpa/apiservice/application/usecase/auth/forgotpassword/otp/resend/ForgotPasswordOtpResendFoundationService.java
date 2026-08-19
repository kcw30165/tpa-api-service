package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class ForgotPasswordOtpResendFoundationService
    implements EvaluateForgotPasswordOtpResendUseCase {
  private final ForgotPasswordOtpResendEligibilityValidator validator;

  public ForgotPasswordOtpResendFoundationService(ForgotPasswordOtpResendEligibilityValidator v) {
    validator = Objects.requireNonNull(v);
  }

  public Mono<ForgotPasswordOtpResendResult> evaluate(ForgotPasswordOtpResendCommand c) {
    if (c == null
        || c.mutationLock() == null
        || !c.mutationLock().acquired()
        || c.lockReloadedSession() == null)
      return Mono.just(
          new ForgotPasswordOtpResendResult(
              ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID, null, 0));
    var i =
        new ForgotPasswordOtpResendEligibilityInput(
            AuthFlowScenario.FORGOT_PASSWORD,
            c.lockReloadedSession().status(),
            c.lockReloadedSession().authFlowSessionId().value(),
            c.authoritativeChallenge(),
            c.protectedPhase(),
            c.successfulResendCount(),
            c.now());
    var r = validator.validate(i);
    return Mono.just(
        new ForgotPasswordOtpResendResult(
            r.status(), r.retryAfterSeconds(), r.successfulResendCount()));
  }
}
