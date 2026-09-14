package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/**
 * Result of executing an operation once: the fully hydrated response returned to that caller and
 * the safe snapshot persisted for replay. Construction enforces matching transport semantics.
 */
public record IdempotentCapturedResponse(
    HydratedIdempotentResponse response, IdempotentTerminalResponseSnapshot snapshot) {
  public IdempotentCapturedResponse {
    Objects.requireNonNull(response, "hydrated response must not be null");
    Objects.requireNonNull(snapshot, "terminal snapshot must not be null");
    if (response.httpStatus() != snapshot.httpStatus()
        || !response.mediaType().equalsIgnoreCase(snapshot.mediaType())
        || !response.safeHeaders().equals(snapshot.safeHeaders())
        || !Objects.equals(
            response.protectedReplayReference(), snapshot.protectedReplayReference())) {
      throw new IllegalArgumentException("hydrated response and terminal snapshot do not match");
    }
  }
}
