package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;
import java.util.regex.Pattern;

public record AuthFlowSessionId(String value) {
  private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

  public AuthFlowSessionId {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank() || !SAFE_ID.matcher(value).matches()) {
      throw new IllegalArgumentException("AuthFlowSessionId is invalid");
    }
  }

  @Override
  public String toString() {
    return "AuthFlowSessionId[value=<redacted>]";
  }
}
