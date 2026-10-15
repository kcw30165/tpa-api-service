package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.util.Locale;
import java.util.regex.Pattern;

public record RegistrationIdempotencyKey(String value) {
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");
  private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");

  public RegistrationIdempotencyKey {
    if (value == null
        || value.isBlank()
        || !SAFE.matcher(value).matches()
        || containsUnsafeToken(value)) {
      throw new IllegalArgumentException("Invalid registration idempotency key");
    }
  }

  private static boolean containsUnsafeToken(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return value.chars().anyMatch(Character::isWhitespace)
        || value.contains(":")
        || value.contains("/")
        || value.contains("@")
        || LONG_DIGITS.matcher(value).find() && value.chars().allMatch(Character::isDigit)
        || lower.contains("csrf_")
        || lower.contains("otp_")
        || lower.contains("auth_flow_sid")
        || lower.contains("bearer")
        || lower.contains("cookie");
  }

  @Override
  public String toString() {
    return "RegistrationIdempotencyKey[redacted]";
  }
}
