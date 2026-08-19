package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;
import java.util.Objects;

public record VerifyForgotPasswordOtpCommand(
    AuthFlowSessionId sessionId,
    String csrfToken,
    String code,
    String origin,
    String referer,
    String idempotencyKey,
    String language,
    String requestId,
    Duration idempotencyTtl) {

  public VerifyForgotPasswordOtpCommand {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
  }

  @Override
  public String toString() {
    return "VerifyForgotPasswordOtpCommand[sessionId=<redacted>, csrfToken=<redacted>, code=<redacted>, origin=<redacted>, referer=<redacted>, idempotencyKey=<redacted>, language="
        + language
        + ", requestId=<redacted>, idempotencyTtl="
        + idempotencyTtl
        + "]";
  }
}
