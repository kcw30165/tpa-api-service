package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Context-neutral execution contract for ownership-checked reservation, completion, and replay.
 * Action integrations supply protected execution, response capture, hydration, and conservative
 * failure classification without exposing persistence implementation details.
 */
public interface IdempotencyExecutor {
  Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command);

  Mono<Optional<IdempotentExecutionResult>> replayCompleted(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      ProtectedReplayResolver replayResolver);
}
