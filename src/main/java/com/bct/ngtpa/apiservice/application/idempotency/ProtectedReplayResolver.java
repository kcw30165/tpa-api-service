package com.bct.ngtpa.apiservice.application.idempotency;

import reactor.core.publisher.Mono;

/**
 * Hydrates a persisted safe snapshot. If protected material is absent, invalid, expired, or cannot
 * be authorized for the current context, resolution fails closed without executing the operation.
 */
public interface ProtectedReplayResolver {
  Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot);
}
