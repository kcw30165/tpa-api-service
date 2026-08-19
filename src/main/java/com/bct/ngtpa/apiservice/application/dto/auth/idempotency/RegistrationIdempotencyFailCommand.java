package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record RegistrationIdempotencyFailCommand(
    RegistrationIdempotencyAction action,
    RegistrationIdempotencyKey idempotencyKey,
    RegistrationRequestHash requestHash,
    String reasonCode,
    Instant failedAt,
    Duration ttl) {
  private static final Pattern SAFE_REASON =
      Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+$");

  public RegistrationIdempotencyFailCommand {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(requestHash, "requestHash must not be null");
    Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    Objects.requireNonNull(failedAt, "failedAt must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (!SAFE_REASON.matcher(reasonCode).matches()) {
      throw new IllegalArgumentException("Invalid idempotency failure reason code");
    }
    if (!ttl.isPositive()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
  }

  @Override
  public String toString() {
    return "RegistrationIdempotencyFailCommand[action="
        + action
        + ", reasonCode="
        + reasonCode
        + ", failedAt="
        + failedAt
        + ", ttl="
        + ttl
        + ", idempotencyKey=<redacted>, requestHash=<redacted>]";
  }
}
