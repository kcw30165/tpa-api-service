package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Complete context-neutral identity for one idempotent operation. */
public record IdempotencyOperationId(
    IdempotencyCapability capability,
    IdempotencyAction action,
    IdempotencyOperationScope scope,
    IdempotencyKey key) {
  public IdempotencyOperationId {
    Objects.requireNonNull(capability, "capability must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(scope, "scope must not be null");
    Objects.requireNonNull(key, "key must not be null");
  }

  @Override
  public String toString() {
    return "IdempotencyOperationId[capability="
        + capability.value()
        + ", action="
        + action.value()
        + ", scope=<redacted>, key=<redacted>]";
  }
}
