package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/**
 * Opaque, non-secret operation scope. It does not expose a session, actor, account, or workflow
 * model and must be derived by an authorized action integration.
 */
public record IdempotencyOperationScope(String value) {
  private static final String PATTERN = "[A-Za-z0-9_-]{8,128}";

  public IdempotencyOperationScope {
    Objects.requireNonNull(value, "idempotency operation scope must not be null");
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("idempotency operation scope is invalid");
    }
  }

  @Override
  public String toString() {
    return "IdempotencyOperationScope[value=<redacted>]";
  }
}
