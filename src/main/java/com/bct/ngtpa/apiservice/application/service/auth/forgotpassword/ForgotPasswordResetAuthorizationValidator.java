package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.time.Instant;
import java.util.Objects;

public class ForgotPasswordResetAuthorizationValidator {

  public ForgotPasswordResetAuthorizationValidationResult validate(
      ForgotPasswordResetAuthorization authorization,
      AuthFlowSession session,
      AuthFlowSessionId expectedSessionId,
      String expectedResetSubjectId,
      Instant now) {
    Objects.requireNonNull(authorization, "authorization must not be null");
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(expectedSessionId, "expectedSessionId must not be null");
    Objects.requireNonNull(expectedResetSubjectId, "expectedResetSubjectId must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (!expectedSessionId.value().equals(authorization.authFlowSessionId())) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.WRONG_SESSION);
    }
    if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.WRONG_SCENARIO);
    }
    if (!expectedResetSubjectId.equals(authorization.resetSubjectId())) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.WRONG_SUBJECT);
    }
    if (authorization.consumedAt() != null) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.CONSUMED);
    }
    if (!now.isBefore(authorization.expiresAt())) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.EXPIRED);
    }
    if (session.status() == AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.UNKNOWN_OUTCOME_BLOCKED);
    }
    if (session.isExpired(now) || session.isTerminal()) {
      return ForgotPasswordResetAuthorizationValidationResult.of(
          ForgotPasswordResetAuthorizationValidationStatus.TERMINAL_SESSION);
    }
    return ForgotPasswordResetAuthorizationValidationResult.of(
        ForgotPasswordResetAuthorizationValidationStatus.VALID);
  }
}
