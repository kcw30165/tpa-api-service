package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;

/** Field-specific masking that remains useful for investigation without logging raw PII. */
final class RegistrationLookupLogMasker {
  private RegistrationLookupLogMasker() {}

  static String identity(String value) {
    String text = trim(value);
    if (text == null) return null;
    if (text.length() <= 3) return "*".repeat(text.length());
    int suffix = Math.min(3, text.length());
    return "*".repeat(text.length() - suffix) + text.substring(text.length() - suffix);
  }

  static String dateOfBirth(String value) {
    String text = trim(value);
    if (text == null) return null;
    int separator = text.lastIndexOf('/');
    return separator >= 0 && separator + 1 < text.length()
        ? "**/**/" + text.substring(separator + 1)
        : "<masked>";
  }

  static String contact(RegistrationContactType contactType, String value) {
    return contactType == RegistrationContactType.EMAIL ? email(value) : mobile(value);
  }

  static String email(String value) {
    String text = trim(value);
    if (text == null) return null;
    int at = text.indexOf('@');
    if (at <= 0 || at == text.length() - 1) return "<masked-email>";
    String local = text.substring(0, at);
    String domain = text.substring(at + 1);
    String maskedLocal =
        local.length() == 1
            ? "*"
            : local.substring(0, 1)
                + "*".repeat(Math.max(1, local.length() - 2))
                + local.substring(local.length() - 1);
    return maskedLocal + "@" + domain;
  }

  static String mobile(String value) {
    String text = trim(value);
    if (text == null) return null;
    int suffix = Math.min(4, text.length());
    return "*".repeat(Math.max(0, text.length() - suffix)) + text.substring(text.length() - suffix);
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
