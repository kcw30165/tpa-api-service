package com.bct.ngtpa.apiservice.application.idempotency;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Shared command carrying no session, actor, account, feature, or framework-specific model. */
public record IdempotentExecutionCommand(
    IdempotencyOperationId operationId,
    IdempotencyFingerprint fingerprint,
    Duration ttl,
    Supplier<Mono<HydratedIdempotentResponse>> protectedOperation,
    IdempotentResponseCapture responseCapture,
    ProtectedReplayResolver replayResolver) {
  public IdempotentExecutionCommand {
    Objects.requireNonNull(operationId, "operation id must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    Objects.requireNonNull(protectedOperation, "protected operation must not be null");
    Objects.requireNonNull(responseCapture, "response capture must not be null");
    Objects.requireNonNull(replayResolver, "replay resolver must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("idempotency TTL must be positive");
    }
  }
}
