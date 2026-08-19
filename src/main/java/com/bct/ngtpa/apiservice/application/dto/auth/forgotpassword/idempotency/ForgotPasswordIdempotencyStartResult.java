package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.util.Objects;

public record ForgotPasswordIdempotencyStartResult(
    ForgotPasswordIdempotencyStartOutcome outcome, ForgotPasswordIdempotencyRecord record) {

  public ForgotPasswordIdempotencyStartResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(record, "record must not be null");
  }
}
