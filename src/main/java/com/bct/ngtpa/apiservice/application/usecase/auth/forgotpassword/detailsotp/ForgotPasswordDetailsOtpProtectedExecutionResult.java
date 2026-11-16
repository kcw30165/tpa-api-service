package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import java.util.Objects;

/** Protected operation result carrying transient public data and opaque replay continuity. */
public record ForgotPasswordDetailsOtpProtectedExecutionResult(
    ForgotPasswordDetailsOtpResult result,
    ProtectedReplayReference replayReference,
    FailureOrigin failureOrigin,
    String failureStage) {

  public ForgotPasswordDetailsOtpProtectedExecutionResult {
    Objects.requireNonNull(result, "result must not be null");
    if ((result.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS)
        != (replayReference != null)) {
      throw new IllegalArgumentException("success and protected replay reference must agree");
    }
    if ((failureOrigin == null) != (failureStage == null)) {
      throw new IllegalArgumentException("technical failure origin and stage must agree");
    }
    if (failureStage != null && failureStage.isBlank()) {
      throw new IllegalArgumentException("technical failure stage must not be blank");
    }
  }
}
