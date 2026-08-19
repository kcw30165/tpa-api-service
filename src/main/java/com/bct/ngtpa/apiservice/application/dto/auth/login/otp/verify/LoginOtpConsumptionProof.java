package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

import java.time.Instant;

public record LoginOtpConsumptionProof(
    String reservationHash,
    AuthenticatedLoginHandoffReference handoffReference,
    LoginOtpVerificationOperationReference operationReference,
    String challengeFingerprint,
    Instant committedAt) {
  public LoginOtpConsumptionProof {
    if (reservationHash == null
        || handoffReference == null
        || operationReference == null
        || challengeFingerprint == null
        || committedAt == null) throw new IllegalArgumentException("complete proof required");
  }

  @Override
  public String toString() {
    return "LoginOtpConsumptionProof[<redacted>]";
  }
}
