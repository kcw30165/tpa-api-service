package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationResult;
import java.time.Instant;
import reactor.core.publisher.Mono;

/**
 * Typed cancellation capability shared by session families.
 *
 * @param <S> session identifier type
 * @param <R> cancellation reason type
 */
public interface SessionCancellationPort<S, R> {
  Mono<SessionCancellationResult> cancel(S sessionId, R reason, Instant cancelledAt);
}
