package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.time.*;

public record LoginOtpSendCompleteCommand(
    LoginIdempotencyAction action,
    String idempotencyKey,
    LoginOtpSendRequestHash requestHash,
    LoginOtpSendReplaySnapshot replay,
    Instant now,
    Duration ttl) {
  @Override
  public String toString() {
    return "LoginOtpSendCompleteCommand[idempotencyKey=<redacted>, requestHash=<redacted>, replay="
        + replay
        + ", now="
        + now
        + ", ttl="
        + ttl
        + "]";
  }
}
