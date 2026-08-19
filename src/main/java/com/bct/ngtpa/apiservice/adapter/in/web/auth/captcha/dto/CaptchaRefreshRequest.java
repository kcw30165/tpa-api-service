package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto;

public record CaptchaRefreshRequest(String captchaChallengeId) {

  @Override
  public String toString() {
    return "CaptchaRefreshRequest[captchaChallengeId=<redacted>]";
  }
}
