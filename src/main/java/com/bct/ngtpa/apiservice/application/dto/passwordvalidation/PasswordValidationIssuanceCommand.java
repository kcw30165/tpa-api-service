package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.util.Objects;

public record PasswordValidationIssuanceCommand(
    AuthenticatedSessionId sessionId,
    PortalAccessContext context,
    String password,
    PasswordValidationPurpose purpose,
    String operationPayloadJson,
    PasswordValidationPurposePayload payload,
    String correlationId) {

  public PasswordValidationIssuanceCommand {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(password, "password must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(operationPayloadJson, "operationPayloadJson must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  @Override
  public String toString() {
    return "PasswordValidationIssuanceCommand[<redacted>]";
  }
}
