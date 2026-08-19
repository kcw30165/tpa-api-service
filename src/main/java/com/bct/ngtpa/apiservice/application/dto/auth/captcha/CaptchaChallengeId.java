package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;
import java.util.regex.Pattern;

public record CaptchaChallengeId(String value) {
  private static final Pattern SAFE_ID = Pattern.compile("^cap_[A-Za-z0-9_-]{16,128}$");

  public CaptchaChallengeId {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank() || !SAFE_ID.matcher(value).matches()) {
      throw new IllegalArgumentException("CaptchaChallengeId is invalid");
    }
  }

  @Override
  public String toString() {
    return "CaptchaChallengeId[value=<redacted>]";
  }
}
