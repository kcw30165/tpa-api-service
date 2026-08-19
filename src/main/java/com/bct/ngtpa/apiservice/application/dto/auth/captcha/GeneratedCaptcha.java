package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record GeneratedCaptcha(String answer, CaptchaImage image) {

  public GeneratedCaptcha {
    Objects.requireNonNull(answer, "answer must not be null");
    Objects.requireNonNull(image, "image must not be null");
    if (answer.isBlank()) {
      throw new IllegalArgumentException("GeneratedCaptcha answer is invalid");
    }
  }

  @Override
  public String toString() {
    return "GeneratedCaptcha[answer=<redacted>, image=<redacted>]";
  }
}
