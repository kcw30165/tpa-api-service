package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;

public record PasswordValidationIssuanceCommand(
    AuthenticatedSessionId sessionId,
    PortalAccessContext context,
    String password,
    PasswordValidationPurpose purpose,
    PayloadDigest binding,
    String correlationId) {
  @Override
  public String toString() {
    return "PasswordValidationIssuanceCommand[<redacted>]";
  }
}
