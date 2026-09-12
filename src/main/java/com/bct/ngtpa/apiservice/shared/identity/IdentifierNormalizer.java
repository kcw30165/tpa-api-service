package com.bct.ngtpa.apiservice.shared.identity;

import java.util.Locale;

/**
 * Canonical normalization + equality for user-facing identifiers (username, TAS cust-id) that may
 * differ only by surrounding whitespace or letter case between systems. Both sides of any identity
 * comparison must be normalized through this helper so downstream case/whitespace variance can never
 * cause a false mismatch.
 */
public final class IdentifierNormalizer {
  private IdentifierNormalizer() {}

  /** Trims the value; returns {@code null} for {@code null}/blank input. */
  public static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Compares two identifiers after normalization, case-insensitively. Returns {@code false} when
   * either normalized value is {@code null} (a missing/blank identity must never be treated as a
   * match).
   */
  public static boolean equalsNormalized(String left, String right) {
    String a = normalize(left);
    String b = normalize(right);
    if (a == null || b == null) {
      return false;
    }
    return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
  }
}
