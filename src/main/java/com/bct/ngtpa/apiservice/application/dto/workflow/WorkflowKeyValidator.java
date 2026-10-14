package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Locale;
import java.util.regex.Pattern;

final class WorkflowKeyValidator {
  private static final Pattern CODE_LIKE =
      Pattern.compile("^[a-z][A-Za-z0-9]*+(\\.[a-z][A-Za-z0-9]*+)++$");
  private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");

  private WorkflowKeyValidator() {}

  static String validate(String value, String safeErrorMessage) {
    if (value == null) {
      throw new IllegalArgumentException(safeErrorMessage);
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()
        || !CODE_LIKE.matcher(trimmed).matches()
        || containsUnsafeToken(trimmed)) {
      throw new IllegalArgumentException(safeErrorMessage);
    }
    return trimmed;
  }

  private static boolean containsUnsafeToken(String value) {
    // CODE_LIKE already guarantees `value` contains no whitespace, '@', or
    // '_', so the checks for those characters (and underscore-based tokens
    // such as "csrf_"/"otp_") are unreachable here and were removed as
    // dead code (confirmed via JaCoCo branch coverage: always-false).
    String lower = value.toLowerCase(Locale.ROOT);
    return LONG_DIGITS.matcher(value).find()
        || isUnsafePasswordLikeKey(lower)
        || lower.contains("bearer")
        || lower.contains("cookie");
  }

  private static boolean isUnsafePasswordLikeKey(String lower) {
    // CODE_LIKE guarantees `lower` contains at least one literal '.', so an
    // exact-equality check against the dot-free literal "password" could
    // never match and was removed as dead code (confirmed via JaCoCo:
    // always false).
    return lower.startsWith("password.")
        || (lower.endsWith(".password") && !lower.startsWith("err.login."))
        || (lower.contains(".password.")
            && !lower.startsWith("registration.password.")
            && !lower.startsWith("err.registration.password.")
            && !lower.startsWith("err.login."));
  }
}
