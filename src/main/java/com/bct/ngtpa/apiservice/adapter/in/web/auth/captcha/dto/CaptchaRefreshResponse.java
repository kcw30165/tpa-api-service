package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto;

public record CaptchaRefreshResponse(
    String captchaChallengeId, String imageBase64, String mimeType, Integer expiresInSeconds) {

  @Override
  public String toString() {
    return "CaptchaRefreshResponse[captchaChallengeId=<redacted>, imageBase64=<redacted>, mimeType="
        + mimeType
        + ", expiresInSeconds="
        + expiresInSeconds
        + "]";
  }
}
