package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

public record CheckRegistrationUsernameCommand(
    AuthFlowSessionId authFlowSessionId,
    String csrfToken,
    RegistrationSelectedTrust selectedTrust,
    String username,
    String origin,
    String referer,
    String acceptLanguage,
    String correlationId) {
  @Override
  public String toString() {
    return "CheckRegistrationUsernameCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>"
        + ", selectedTrust="
        + selectedTrust
        + ", username=<redacted>, origin="
        + origin
        + ", referer="
        + referer
        + ", acceptLanguage="
        + acceptLanguage
        + ", correlationId="
        + correlationId
        + "]";
  }
}
