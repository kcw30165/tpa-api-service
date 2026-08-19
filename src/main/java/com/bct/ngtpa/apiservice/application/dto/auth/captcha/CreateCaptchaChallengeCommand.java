package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

public record CreateCaptchaChallengeCommand(String correlationId, String acceptLanguage) {

  @Override
  public String toString() {
    return "CreateCaptchaChallengeCommand[correlationId=<redacted>, acceptLanguage=<redacted>]";
  }
}
