package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Opaque client-provided idempotency key. */
public record IdempotencyKey(String value) {
  private static final String PATTERN = "[A-Za-z0-9_-]{8,128}";

  public IdempotencyKey {
    Objects.requireNonNull(value, "idempotency key must not be null");
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("idempotency key is invalid");
    }
  }

  @Override
  public String toString() {
    return "IdempotencyKey[value=<redacted>]";
  }
}
