package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record CaptchaImage(String imageBase64, String mimeType) {

  public CaptchaImage {
    Objects.requireNonNull(imageBase64, "imageBase64 must not be null");
    Objects.requireNonNull(mimeType, "mimeType must not be null");
    if (imageBase64.isBlank()) {
      throw new IllegalArgumentException("CaptchaImage imageBase64 is invalid");
    }
    if (mimeType.isBlank()) {
      throw new IllegalArgumentException("CaptchaImage mimeType is invalid");
    }
  }

  @Override
  public String toString() {
    return "CaptchaImage[imageBase64=<redacted>, mimeType=" + mimeType + "]";
  }
}
