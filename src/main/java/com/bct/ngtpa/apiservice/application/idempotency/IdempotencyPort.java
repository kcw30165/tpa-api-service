package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Shared persistence boundary. Implemented in P2; no feature or session dependency is permitted.
 */
public interface IdempotencyPort {
  Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve command);

  Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete command);

  Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail command);

  Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown command);

  Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId operationId);
}
