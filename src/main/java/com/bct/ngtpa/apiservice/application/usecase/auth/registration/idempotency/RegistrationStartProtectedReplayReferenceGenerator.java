package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;

@FunctionalInterface
public interface RegistrationStartProtectedReplayReferenceGenerator {
  ProtectedReplayReference generate();
}
