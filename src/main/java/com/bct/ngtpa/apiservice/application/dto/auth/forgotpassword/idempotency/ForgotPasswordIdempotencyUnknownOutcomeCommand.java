package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordIdempotencyUnknownOutcomeCommand(
    ForgotPasswordIdempotencyAction action,
    ForgotPasswordIdempotencyKey idempotencyKey,
    ForgotPasswordRequestHash requestHash,
    Instant recordedAt,
    Duration ttl) {

  public ForgotPasswordIdempotencyUnknownOutcomeCommand {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(requestHash, "requestHash must not be null");
    Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
  }
}
