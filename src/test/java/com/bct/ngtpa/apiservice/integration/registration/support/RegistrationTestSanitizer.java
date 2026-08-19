package com.bct.ngtpa.apiservice.integration.registration.support;

import java.util.regex.Pattern;

public final class RegistrationTestSanitizer {

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern OTP = Pattern.compile("(?i)(otp|code)(\\s*[:=]?\\s*)\\d{4,8}");
  private static final Pattern SECRET =
      Pattern.compile(
          "(?i)(password|token|secret|csrf|auth_flow_sid)(\\s*[:=]?\\s*)[^,;\\s]+",
          Pattern.CASE_INSENSITIVE);

  private RegistrationTestSanitizer() {}

  public static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String safe = EMAIL.matcher(value).replaceAll("<redacted-email>");
    safe = OTP.matcher(safe).replaceAll("$1$2<redacted>");
    safe = SECRET.matcher(safe).replaceAll("$1$2<redacted>");
    return safe.replaceAll("[\\r\\n]+", " ").trim();
  }
}
