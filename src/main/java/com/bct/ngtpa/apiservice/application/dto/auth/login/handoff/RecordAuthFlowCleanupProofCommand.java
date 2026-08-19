package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.time.Instant;
import java.util.Objects;

public record RecordAuthFlowCleanupProofCommand(
    AuthenticatedLoginHandoffReference handoffReference,
    AuthenticatedSessionId authenticatedSessionId,
    String cleanupProof,
    Instant deletedAt) {
  public RecordAuthFlowCleanupProofCommand {
    Objects.requireNonNull(handoffReference);
    Objects.requireNonNull(authenticatedSessionId);
    Objects.requireNonNull(deletedAt);
    if (cleanupProof == null || cleanupProof.isBlank())
      throw new IllegalArgumentException("cleanupProof required");
  }

  @Override
  public String toString() {
    return "RecordAuthFlowCleanupProofCommand[<redacted>]";
  }
}
