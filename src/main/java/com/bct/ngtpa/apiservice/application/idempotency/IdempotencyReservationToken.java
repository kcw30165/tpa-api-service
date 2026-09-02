package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Opaque ownership proof for the executor that acquired a PROCESSING reservation. */
public record IdempotencyReservationToken(String value) {
  private static final String PATTERN = "[A-Za-z0-9_-]{32,128}";

  public IdempotencyReservationToken {
    Objects.requireNonNull(value, "idempotency reservation token must not be null");
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("idempotency reservation token is invalid");
    }
  }

  @Override
  public String toString() {
    return "IdempotencyReservationToken[value=<redacted>]";
  }
}
