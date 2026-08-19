package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.time.*;
import java.util.Objects;

public record LoginIdempotencyStartCommand(
    LoginIdempotencyAction action,
    LoginIdempotencyKey idempotencyKey,
    LoginCredentialRequestHash requestHash,
    Instant now,
    Duration ttl) {
  public LoginIdempotencyStartCommand {
    Objects.requireNonNull(action);
    Objects.requireNonNull(idempotencyKey);
    Objects.requireNonNull(requestHash);
    Objects.requireNonNull(now);
    if (ttl == null || ttl.isZero() || ttl.isNegative())
      throw new IllegalArgumentException("ttl must be positive");
  }

  public Instant expiresAt() {
    return now.plus(ttl);
  }
}
