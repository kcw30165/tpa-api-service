package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record RecordAuthenticatedSessionCommittedCommand(
    AuthenticatedLoginHandoffReference handoffReference,
    LoginOtpVerificationOperationReference operationReference,
    String reservationHash,
    AuthenticatedSessionId authenticatedSessionId) {
  public RecordAuthenticatedSessionCommittedCommand {
    Objects.requireNonNull(handoffReference);
    Objects.requireNonNull(operationReference);
    Objects.requireNonNull(authenticatedSessionId);
    if (reservationHash == null || reservationHash.isBlank())
      throw new IllegalArgumentException("reservationHash required");
  }

  @Override
  public String toString() {
    return "RecordAuthenticatedSessionCommittedCommand[<redacted>]";
  }
}
