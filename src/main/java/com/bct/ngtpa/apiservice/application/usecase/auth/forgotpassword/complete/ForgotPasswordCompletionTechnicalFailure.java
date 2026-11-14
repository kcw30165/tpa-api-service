package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import java.util.Objects;

/** Cause-free technical classification for one Forgot Password reset exchange. */
public final class ForgotPasswordCompletionTechnicalFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final FailureOrigin origin;
  private final String stage;

  public ForgotPasswordCompletionTechnicalFailure(FailureOrigin origin, String stage) {
    super("Forgot Password completion failed", null, false, false);
    this.origin = Objects.requireNonNull(origin, "origin must not be null");
    this.stage = Objects.requireNonNull(stage, "stage must not be null");
  }

  public FailureOrigin origin() {
    return origin;
  }

  public String stage() {
    return stage;
  }
}
