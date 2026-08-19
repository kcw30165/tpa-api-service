package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto;

public record CaptchaVerifyRequest(String captchaChallengeId, String answer) {

  @Override
  public String toString() {
    return "CaptchaVerifyRequest[captchaChallengeId=<redacted>, answer=<redacted>]";
  }
}
