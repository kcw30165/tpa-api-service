package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.Objects;

/** Protected operation result carrying transient public data and opaque replay continuity. */
public record ForgotPasswordDetailsOtpProtectedExecution(
    ForgotPasswordDetailsOtpResult result, ProtectedReplayReference replayReference) {
  public ForgotPasswordDetailsOtpProtectedExecution {
    Objects.requireNonNull(result, "result must not be null");
    if ((result.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS)
        != (replayReference != null)) {
      throw new IllegalArgumentException("success and protected replay reference must agree");
    }
  }
}
