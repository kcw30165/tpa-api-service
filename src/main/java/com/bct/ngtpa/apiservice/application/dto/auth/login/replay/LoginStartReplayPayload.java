package com.bct.ngtpa.apiservice.application.dto.auth.login.replay;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Sensitive replay material. Instances must be persisted only after protection. */
public record LoginStartReplayPayload(
    String authFlowSessionId,
    String csrfToken,
    int expiresInSeconds,
    List<LoginDestinationChoice> destinations,
    Instant expiresAt) {
  public LoginStartReplayPayload {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(csrfToken, "csrfToken must not be null");
    if (expiresInSeconds <= 0)
      throw new IllegalArgumentException("expiresInSeconds must be positive");
    destinations = destinations == null ? List.of() : List.copyOf(destinations);
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  @Override
  public String toString() {
    return "LoginStartReplayPayload[authFlowSessionId=<redacted>, csrfToken=<redacted>, expiresInSeconds="
        + expiresInSeconds
        + ", destinationsCount="
        + destinations.size()
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
