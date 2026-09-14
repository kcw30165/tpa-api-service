package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;

@FunctionalInterface
public interface UsernameRecoveryStartProtectedReplayReferenceGenerator {
  ProtectedReplayReference generate();
}
