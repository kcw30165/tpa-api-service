package com.bct.ngtpa.apiservice.application.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Context-neutral commands accepted by the shared persistence port. */
public final class IdempotencyCommands {
  private IdempotencyCommands() {}

  public record Reserve(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      Instant now,
      Duration ttl) {
    public Reserve {
      validate(operationId, fingerprint, now, ttl);
    }
  }

  public record Complete(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      IdempotentTerminalResponseSnapshot terminalResponse,
      Instant now,
      Duration ttl) {
    public Complete {
      validate(operationId, fingerprint, now, ttl);
      Objects.requireNonNull(terminalResponse, "terminal response must not be null");
    }
  }

  public record Fail(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      Instant now,
      Duration ttl) {
    public Fail {
      validate(operationId, fingerprint, now, ttl);
    }
  }

  public record MarkUnknown(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      Instant now,
      Duration ttl) {
    public MarkUnknown {
      validate(operationId, fingerprint, now, ttl);
    }
  }

  private static void validate(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      Instant now,
      Duration ttl) {
    Objects.requireNonNull(operationId, "operation id must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("idempotency TTL must be positive");
    }
  }
}
