package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.util.regex.Pattern;

public record AuthenticatedSessionId(String value) {
  private static final Pattern SAFE = Pattern.compile("^sid_[A-Za-z0-9_-]{20,124}$");

  public AuthenticatedSessionId {
    if (value == null || !SAFE.matcher(value).matches())
      throw new IllegalArgumentException("Invalid authenticated session id");
  }

  @Override
  public String toString() {
    return "AuthenticatedSessionId[redacted]";
  }
}
