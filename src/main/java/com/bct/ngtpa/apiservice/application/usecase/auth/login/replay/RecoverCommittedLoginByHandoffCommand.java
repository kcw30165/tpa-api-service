package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import java.util.Objects;

/** Requests committed authenticated-login recovery by opaque handoff identity. */
public record RecoverCommittedLoginByHandoffCommand(
    AuthenticatedLoginHandoffReference handoffReference, String correlationId) {
  public RecoverCommittedLoginByHandoffCommand {
    Objects.requireNonNull(handoffReference, "handoffReference must not be null");
    correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
  }
}
