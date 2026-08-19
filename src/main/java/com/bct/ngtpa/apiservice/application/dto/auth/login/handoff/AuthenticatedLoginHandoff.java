package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.time.Instant;
import java.util.Objects;

public record AuthenticatedLoginHandoff(
    AuthenticatedLoginHandoffReference handoffReference,
    LoginOtpVerificationOperationReference operationReference,
    String reservationHash,
    AuthenticatedLoginHandoffStatus status,
    AuthenticatedSessionId authenticatedSessionId,
    String cleanupProof,
    Instant createdAt,
    Instant expiresAt) {
  public AuthenticatedLoginHandoff {
    Objects.requireNonNull(handoffReference);
    Objects.requireNonNull(operationReference);
    if (reservationHash == null || reservationHash.isBlank())
      throw new IllegalArgumentException("reservationHash required");
    Objects.requireNonNull(status);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(expiresAt);
    if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("handoff expiry invalid");
    if (status == AuthenticatedLoginHandoffStatus.AUTHENTICATED_SESSION_COMMITTED
        && authenticatedSessionId == null)
      throw new IllegalArgumentException("committed handoff requires authenticated session");
    if (status == AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED
        && (authenticatedSessionId == null || cleanupProof == null || cleanupProof.isBlank()))
      throw new IllegalArgumentException("completed cleanup requires session and proof");
  }

  @Override
  public String toString() {
    return "AuthenticatedLoginHandoff[status="
        + status
        + ", sessionPresent="
        + (authenticatedSessionId != null)
        + ", cleanupProofPresent="
        + (cleanupProof != null)
        + "]";
  }
}
