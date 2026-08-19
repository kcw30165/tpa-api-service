package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.time.Instant;
import java.util.Objects;

public record CreateAuthenticatedLoginHandoffCommand(
    LoginOtpConsumptionProof proof,
    AuthenticatedSessionId proposedAuthenticatedSessionId,
    Instant expiresAt) {
  public CreateAuthenticatedLoginHandoffCommand {
    Objects.requireNonNull(proof);
    Objects.requireNonNull(proposedAuthenticatedSessionId);
    Objects.requireNonNull(expiresAt);
    if (!expiresAt.isAfter(proof.committedAt()))
      throw new IllegalArgumentException("handoff expiry invalid");
  }

  @Override
  public String toString() {
    return "CreateAuthenticatedLoginHandoffCommand[<redacted>]";
  }
}
