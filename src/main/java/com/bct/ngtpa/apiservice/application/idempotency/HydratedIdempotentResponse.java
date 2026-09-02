package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Map;
import java.util.Objects;

/** Fully hydrated response emitted after optional protected replay resolution. Never persisted. */
public record HydratedIdempotentResponse(
    int httpStatus, String mediaType, String serializedBody, Map<String, String> safeHeaders) {
  public HydratedIdempotentResponse {
    if (httpStatus < 100 || httpStatus > 599) {
      throw new IllegalArgumentException("hydrated response HTTP status is invalid");
    }
    Objects.requireNonNull(mediaType, "hydrated response media type must not be null");
    Objects.requireNonNull(serializedBody, "hydrated response body must not be null");
    if (serializedBody.isBlank()) {
      throw new IllegalArgumentException("hydrated response body must not be blank");
    }
    safeHeaders = IdempotencySafeHeaders.copyOf(safeHeaders);
  }
}
