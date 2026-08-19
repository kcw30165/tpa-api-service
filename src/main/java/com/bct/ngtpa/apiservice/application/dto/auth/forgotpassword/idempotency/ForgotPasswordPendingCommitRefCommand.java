package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.time.Duration;
import java.util.Objects;

public record ForgotPasswordPendingCommitRefCommand(
    ForgotPasswordIdempotencyAction action,
    ForgotPasswordIdempotencyKey idempotencyKey,
    ForgotPasswordRequestHash requestHash,
    String opaqueCommitRef,
    Duration ttl) {
  public ForgotPasswordPendingCommitRefCommand {
    Objects.requireNonNull(action);
    Objects.requireNonNull(idempotencyKey);
    Objects.requireNonNull(requestHash);
    if (opaqueCommitRef == null || !opaqueCommitRef.matches("^[A-Za-z0-9_-]{22,128}$"))
      throw new IllegalArgumentException("invalid opaque commit reference");
    Objects.requireNonNull(ttl);
  }
}
