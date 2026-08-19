package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.util.Objects;

public record LoginIdempotencyStartResult(
    LoginIdempotencyStartOutcome outcome, LoginIdempotencyRecord record) {
  public LoginIdempotencyStartResult {
    Objects.requireNonNull(outcome);
    Objects.requireNonNull(record);
  }
}
