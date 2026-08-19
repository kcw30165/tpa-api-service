package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RegistrationIdempotencyUnknownOutcomeCommand(
    RegistrationIdempotencyAction action,
    RegistrationIdempotencyKey idempotencyKey,
    RegistrationRequestHash requestHash,
    Instant recordedAt,
    Duration ttl) {

  public RegistrationIdempotencyUnknownOutcomeCommand {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(requestHash, "requestHash must not be null");
    Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (!ttl.isPositive()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
  }

  @Override
  public String toString() {
    return "RegistrationIdempotencyUnknownOutcomeCommand[action="
        + action
        + ", recordedAt="
        + recordedAt
        + ", ttl="
        + ttl
        + ", idempotencyKey=<redacted>, requestHash=<redacted>]";
  }
}
