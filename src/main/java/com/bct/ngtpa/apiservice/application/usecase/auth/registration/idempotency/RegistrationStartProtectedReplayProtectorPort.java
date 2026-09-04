package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;

public interface RegistrationStartProtectedReplayProtectorPort {
  ProtectedRegistrationStartReplay protect(
      ProtectedReplayReference reference, RegistrationStartProtectedReplayState state);

  RegistrationStartProtectedReplayState unprotect(
      ProtectedReplayReference reference, ProtectedRegistrationStartReplay protectedReplay);
}
