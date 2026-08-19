package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.util.regex.Pattern;

public record AuthenticatedAccountRef(String value) {
  private static final Pattern SAFE = Pattern.compile("^acc_[A-Za-z0-9_-]{20,124}$");

  public AuthenticatedAccountRef {
    if (value == null || !SAFE.matcher(value).matches())
      throw new IllegalArgumentException("Invalid account reference");
  }

  @Override
  public String toString() {
    return "AuthenticatedAccountRef[redacted]";
  }
}
