package com.bct.ngtpa.apiservice.application.dto.observability;

import java.util.Map;

/** Internal structured failure information. Never serialize this into API responses. */
public record FailureEvent(
    String event,
    String requestId,
    String capability,
    String operation,
    FailureOrigin origin,
    String internalCode,
    String publicCode,
    int httpStatus,
    boolean retryable,
    Map<String, Object> safeDimensions) {

  public FailureEvent {
    safeDimensions = safeDimensions == null ? Map.of() : Map.copyOf(safeDimensions);
  }
}
