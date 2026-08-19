package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

public record RefreshCaptchaChallengeCommand(
    CaptchaChallengeId oldCaptchaChallengeId, String correlationId) {

  @Override
  public String toString() {
    return "RefreshCaptchaChallengeCommand[oldCaptchaChallengeId=<redacted>, correlationId=<redacted>]";
  }
}
