package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Stable action namespace inside an idempotency capability. */
public record IdempotencyAction(String value) {
  private static final String PATTERN = "[a-z][a-z0-9-]{1,63}";

  public IdempotencyAction {
    Objects.requireNonNull(value, "idempotency action must not be null");
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("idempotency action is invalid");
    }
  }
}
