package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Stable capability namespace, independent of a feature, workflow, or session type. */
public record IdempotencyCapability(String value) {
  private static final String PATTERN = "[a-z][a-z0-9-]{1,63}";

  public IdempotencyCapability {
    Objects.requireNonNull(value, "idempotency capability must not be null");
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("idempotency capability is invalid");
    }
  }
}
