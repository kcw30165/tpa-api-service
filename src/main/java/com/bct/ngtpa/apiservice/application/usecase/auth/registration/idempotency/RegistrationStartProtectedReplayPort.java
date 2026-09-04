package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.time.Duration;
import reactor.core.publisher.Mono;

/** Separately encrypted storage used only for Registration Start replay continuity. */
public interface RegistrationStartProtectedReplayPort {
  Mono<ProtectedReplayReference> store(RegistrationStartProtectedReplayState state, Duration ttl);

  Mono<RegistrationStartProtectedReplayState> load(ProtectedReplayReference reference);
}
