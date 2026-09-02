package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import java.util.Map;

/** Schema-versioned wire record with enforced redacted string rendering. */
record RedisIdempotencyRecord(
    int schemaVersion,
    String capability,
    String action,
    String operationScope,
    String idempotencyKey,
    int fingerprintVersion,
    String fingerprint,
    String reservationToken,
    String status,
    RedisTerminalResponse terminalResponse,
    String createdAt,
    String updatedAt,
    String expiresAt) {
  static final int CURRENT_SCHEMA_VERSION = 1;

  @Override
  public String toString() {
    return "RedisIdempotencyRecord[schemaVersion="
        + schemaVersion
        + ", capability="
        + capability
        + ", action="
        + action
        + ", operationScope=<redacted>, idempotencyKey=<redacted>"
        + ", fingerprintVersion="
        + fingerprintVersion
        + ", fingerprint=<redacted>"
        + ", reservationToken=<redacted>, status="
        + status
        + ", terminalResponse="
        + (terminalResponse == null ? "absent" : "present")
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", expiresAt="
        + expiresAt
        + "]";
  }

  record RedisTerminalResponse(
      int schemaVersion,
      int httpStatus,
      String mediaType,
      String serializedSafeBody,
      Map<String, String> safeHeaders,
      String protectedReplayReference) {
    @Override
    public String toString() {
      return "RedisTerminalResponse[schemaVersion="
          + schemaVersion
          + ", httpStatus="
          + httpStatus
          + ", mediaType="
          + mediaType
          + ", serializedSafeBody=<redacted>, safeHeaders="
          + safeHeaders.keySet()
          + ", protectedReplayReference=<redacted>]";
    }
  }
}
