package com.bct.ngtpa.apiservice.architecture;

import java.util.Objects;

/** Formatter-independent assertions for tests that inspect source text. */
public final class SourceTextAssertions {
  private SourceTextAssertions() {}

  public static boolean containsIgnoringWhitespace(CharSequence source, CharSequence token) {
    return compact(source).contains(compact(token));
  }

  public static int indexOfIgnoringWhitespace(CharSequence source, CharSequence token) {
    return compact(source).indexOf(compact(token));
  }

  private static String compact(CharSequence value) {
    Objects.requireNonNull(value, "value must not be null");
    return value.toString().replaceAll("\\s+", "");
  }
}
