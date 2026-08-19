package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import java.util.Objects;

public record InitializeLegacyAuthFlowSessionRevisionCommand(
    AuthFlowSession expectedLegacySession, SessionLockHandle mutationLockHandle) {
  public InitializeLegacyAuthFlowSessionRevisionCommand {
    Objects.requireNonNull(expectedLegacySession, "expectedLegacySession must not be null");
    Objects.requireNonNull(mutationLockHandle, "mutationLockHandle must not be null");
    if (!mutationLockHandle.acquired()) {
      throw new IllegalArgumentException("mutation lock must be acquired");
    }
  }

  @Override
  public String toString() {
    return "InitializeLegacyAuthFlowSessionRevisionCommand[expectedLegacySessionPresent=true, lockProofPresent=true]";
  }
}
