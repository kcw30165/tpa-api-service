package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.List;
import java.util.Objects;

/** Result returned after executing the acquired OTP_RESEND protected mutation. */
public record ForgotPasswordOtpResendProtectedExecutionResult(
    ResendForgotPasswordOtpResult result,
    ProtectedReplayReference replayReference,
    List<ForgotPasswordOtpResendIncident> incidents) {
  public ForgotPasswordOtpResendProtectedExecutionResult(
      ResendForgotPasswordOtpResult result, ProtectedReplayReference replayReference) {
    this(result, replayReference, List.of());
  }

  public ForgotPasswordOtpResendProtectedExecutionResult {
    Objects.requireNonNull(result, "result must not be null");
    incidents = List.copyOf(Objects.requireNonNull(incidents, "incidents must not be null"));
    boolean resent = result.status() == ResendForgotPasswordOtpStatus.RESENT;
    if (resent != (replayReference != null)) {
      throw new IllegalArgumentException(
          "OTP_RESEND protected replay reference must exist only for RESENT");
    }
  }
}
