package com.bct.ngtpa.apiservice.application.port.out.authflow;

import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Scenario-neutral idempotency lifecycle. Type parameters retain each workflow's
 * strong command/key/result types without importing registration DTOs.
 */
public interface AuthFlowIdempotencyPort<A, K, START, START_RESULT, COMPLETE, FAIL, UNKNOWN, RECORD> {
    Mono<START_RESULT> startProcessing(START command);
    Mono<RECORD> markCompleted(COMPLETE command);
    Mono<RECORD> markFailed(FAIL command);
    Mono<RECORD> markUnknownOutcome(UNKNOWN command);
    Mono<Optional<RECORD>> load(A action, K key);
    Mono<Void> delete(A action, K key);
}
