package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

import java.time.Instant;
import java.util.Objects;

public record LoginOtpVerifyReservation(
    String requestHash,
    LoginOtpVerifyReservationStatus status,
    AuthenticatedLoginHandoffReference handoffReference,
    LoginOtpVerificationOperationReference operationReference,
    LoginOtpConsumptionProof proof,
    LoginOtpVerifyPersistenceOutcome attemptOutcome,
    Integer remainingAttempts,
    Instant createdAt,
    Instant expiresAt) {
  public LoginOtpVerifyReservation {
    Objects.requireNonNull(requestHash);
    Objects.requireNonNull(status);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(expiresAt);
    if (!expiresAt.isAfter(createdAt))
      throw new IllegalArgumentException("invalid reservation expiry");
  }

  @Override
  public String toString() {
    return "LoginOtpVerifyReservation[status="
        + status
        + ", referencesPresent="
        + (handoffReference != null && operationReference != null)
        + ", proofPresent="
        + (proof != null)
        + "]";
  }
}
