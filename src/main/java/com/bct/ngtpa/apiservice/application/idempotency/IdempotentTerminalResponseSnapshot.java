package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Map;
import java.util.Objects;

/**
 * Schema-versioned persisted terminal-response snapshot containing safe public response material
 * only. Clear cookies, tokens, session identifiers, credentials, OTPs, identity/contact values, and
 * other protected values are forbidden. Sensitive continuity is represented only by an opaque
 * reference to separately protected replay material. The shared model deliberately does not parse
 * feature JSON; safe-body production is restricted to IdempotentResponseCapture.
 */
public record IdempotentTerminalResponseSnapshot(
    int schemaVersion,
    int httpStatus,
    String mediaType,
    String serializedSafeBody,
    Map<String, String> safeHeaders,
    ProtectedReplayReference protectedReplayReference) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public IdempotentTerminalResponseSnapshot {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("unsupported idempotent response snapshot schema");
    }
    if (httpStatus < 100 || httpStatus > 599) {
      throw new IllegalArgumentException("idempotent response HTTP status is invalid");
    }
    Objects.requireNonNull(mediaType, "idempotent response media type must not be null");
    if (!"application/json".equalsIgnoreCase(mediaType.trim())) {
      throw new IllegalArgumentException("idempotent response media type is unsupported");
    }
    mediaType = "application/json";
    Objects.requireNonNull(serializedSafeBody, "idempotent safe response body must not be null");
    if (serializedSafeBody.isBlank()) {
      throw new IllegalArgumentException("idempotent safe response body must not be blank");
    }
    safeHeaders = IdempotencySafeHeaders.copyOf(safeHeaders);
  }

  @Override
  public String toString() {
    return "IdempotentTerminalResponseSnapshot["
        + "schemaVersion=" + schemaVersion
        + ", httpStatus=" + httpStatus
        + ", mediaType=" + mediaType
        + ", serializedSafeBody=<redacted>"
        + ", safeHeaders=" + safeHeaders.keySet()
        + ", protectedReplayReference=" + protectedReplayReference
        + "]";
  }
}
