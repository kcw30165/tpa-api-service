package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import java.util.Map;
import java.util.Objects;

/** Registration-owned deterministic terminal result before conversion to the shared snapshot. */
public record RegistrationSharedTerminalResponse(
    RegistrationSharedIdempotencyAction action,
    int httpStatus,
    String mediaType,
    String serializedSafeBody,
    Map<String, String> safeHeaders,
    String protectedReplayReference) {
  public RegistrationSharedTerminalResponse {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    if (httpStatus < 100 || httpStatus > 599) {
      throw new IllegalArgumentException("registration terminal HTTP status is invalid");
    }
    mediaType = requireText(mediaType, "registration terminal media type");
    serializedSafeBody = requireText(serializedSafeBody, "registration terminal safe body");
    safeHeaders = safeHeaders == null ? Map.of() : Map.copyOf(safeHeaders);
    protectedReplayReference = trimToNull(protectedReplayReference);
  }

  @Override
  public String toString() {
    return "RegistrationSharedTerminalResponse[action="
        + action
        + ", httpStatus="
        + httpStatus
        + ", mediaType="
        + mediaType
        + ", serializedSafeBody=<redacted>, safeHeaderCount="
        + safeHeaders.size()
        + ", protectedReplayReference="
        + (protectedReplayReference == null ? "absent" : "present")
        + "]";
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value.trim();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
