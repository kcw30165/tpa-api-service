package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.time.Duration;
import reactor.core.publisher.Mono;

/** Separately protected persistence and authorized hydration for START continuity. */
public interface UsernameRecoveryStartProtectedReplayPort {
  Mono<ProtectedReplayReference> store(
      UsernameRecoveryStartProtectedReplayState state, Duration ttl);

  Mono<com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult>
      resolve(ProtectedReplayReference reference);
}
