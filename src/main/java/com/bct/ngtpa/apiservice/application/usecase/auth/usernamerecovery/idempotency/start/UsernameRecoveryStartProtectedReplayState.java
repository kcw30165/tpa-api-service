package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import java.util.Objects;

/** Protected START continuity stored outside the shared safe response snapshot. */
public record UsernameRecoveryStartProtectedReplayState(
    AuthFlowSessionId authFlowSessionId, Instant expiresAt) {
  public UsernameRecoveryStartProtectedReplayState {
    Objects.requireNonNull(authFlowSessionId, "auth-flow session id must not be null");
    Objects.requireNonNull(expiresAt, "replay expiry must not be null");
  }

  @Override
  public String toString() {
    return "UsernameRecoveryStartProtectedReplayState[authFlowSessionId=<redacted>, expiresAt="
        + expiresAt
        + "]";
  }
}
