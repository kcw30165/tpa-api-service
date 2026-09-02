package com.bct.ngtpa.apiservice.application.idempotency;

import reactor.core.publisher.Mono;

/**
 * Context-neutral execution contract. P1 defines the boundary only; orchestration is implemented
 * after the persistence contract is available.
 */
public interface IdempotencyExecutor {
  Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command);
}
