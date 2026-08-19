package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public record ForgotPasswordPendingCommitRefResult(Status status, String opaqueCommitRef) {
  public enum Status {
    ATTACHED,
    EXISTING,
    CONFLICT,
    INVALID_STATE
  }

  public ForgotPasswordPendingCommitRefResult {
    if (opaqueCommitRef == null || opaqueCommitRef.isBlank())
      throw new IllegalArgumentException("opaqueCommitRef required");
  }

  @Override
  public String toString() {
    return "ForgotPasswordPendingCommitRefResult[status=" + status + ",opaqueCommitRef=<redacted>]";
  }
}
