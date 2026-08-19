package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordIdempotencyStartCommand(
    ForgotPasswordIdempotencyAction action,
    ForgotPasswordIdempotencyKey idempotencyKey,
    ForgotPasswordRequestHash requestHash,
    Instant now,
    Instant expiresAt,
    Duration ttl) {

  public ForgotPasswordIdempotencyStartCommand {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(requestHash, "requestHash must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
  }
}
