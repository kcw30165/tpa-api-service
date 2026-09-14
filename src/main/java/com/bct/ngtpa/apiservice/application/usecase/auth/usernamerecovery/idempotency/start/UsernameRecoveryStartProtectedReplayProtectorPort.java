package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;

public interface UsernameRecoveryStartProtectedReplayProtectorPort {
  ProtectedUsernameRecoveryStartReplay protect(
      ProtectedReplayReference reference, UsernameRecoveryStartProtectedReplayState state);

  UsernameRecoveryStartProtectedReplayState unprotect(
      ProtectedReplayReference reference, ProtectedUsernameRecoveryStartReplay value);
}
