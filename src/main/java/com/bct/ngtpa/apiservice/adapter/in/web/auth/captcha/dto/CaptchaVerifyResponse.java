package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto;

public record CaptchaVerifyResponse(
    Boolean verified, String captchaChallengeId, Integer remainingAttempts) {

  @Override
  public String toString() {
    return "CaptchaVerifyResponse[verified="
        + verified
        + ", captchaChallengeId=<redacted>, remainingAttempts="
        + remainingAttempts
        + "]";
  }
}
