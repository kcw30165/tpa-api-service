package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RegistrationIdempotencyStartCommand(
    RegistrationIdempotencyAction action,
    RegistrationIdempotencyKey idempotencyKey,
    RegistrationRequestHash requestHash,
    Instant now,
    Duration ttl) {
  public RegistrationIdempotencyStartCommand {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(requestHash, "requestHash must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (!ttl.isPositive()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
  }

  public Instant expiresAt() {
    return now.plus(ttl);
  }

  @Override
  public String toString() {
    return "RegistrationIdempotencyStartCommand[action="
        + action
        + ", now="
        + now
        + ", ttl="
        + ttl
        + ", idempotencyKey=<redacted>, requestHash=<redacted>]";
  }
}
