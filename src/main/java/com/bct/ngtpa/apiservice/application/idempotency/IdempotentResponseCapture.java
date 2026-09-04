package com.bct.ngtpa.apiservice.application.idempotency;

import reactor.core.publisher.Mono;

/**
 * Reactive action integration boundary that removes protected response material before persistence
 * and stores it separately when required. Empty or failed capture is treated as unknown outcome.
 */
@FunctionalInterface
public interface IdempotentResponseCapture {
  Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response);
}
