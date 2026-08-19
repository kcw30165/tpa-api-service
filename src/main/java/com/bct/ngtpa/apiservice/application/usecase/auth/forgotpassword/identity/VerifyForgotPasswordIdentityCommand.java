package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

public record VerifyForgotPasswordIdentityCommand(
    AuthFlowSessionId sessionId,
    String csrfToken,
    String origin,
    String referer,
    String requestId,
    String identityType,
    String identityValue) {
  @Override
  public String toString() {
    return "VerifyForgotPasswordIdentityCommand[sessionId=<redacted>,csrfToken=<redacted>,origin=<redacted>,referer=<redacted>,requestId=<redacted>,identityType="
        + identityType
        + ",identityValue=<redacted>]";
  }
}
