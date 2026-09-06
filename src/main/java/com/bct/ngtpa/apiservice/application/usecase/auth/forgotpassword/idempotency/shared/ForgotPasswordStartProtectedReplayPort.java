package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.time.Duration;
import reactor.core.publisher.Mono;

/** Separately protected storage for Forgot Password Start replay continuity. */
public interface ForgotPasswordStartProtectedReplayPort {
  Mono<ProtectedReplayReference> store(
      ForgotPasswordStartProtectedReplayState state, Duration ttl);

  Mono<ForgotPasswordStartProtectedReplayState> load(ProtectedReplayReference reference);
}
