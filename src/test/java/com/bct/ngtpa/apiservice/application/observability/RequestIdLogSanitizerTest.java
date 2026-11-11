package com.bct.ngtpa.apiservice.application.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdLogSanitizerTest {
  @Test
  void omitsNullAndBlankRequestIds() {
    assertThat(RequestIdLogSanitizer.sanitize(null)).isNull();
    assertThat(RequestIdLogSanitizer.sanitize(" \t")).isNull();
  }

  @Test
  void replacesDisallowedCharactersAndCountsUnicodeCodePoints() {
    assertThat(RequestIdLogSanitizer.sanitize("id\u0085\u2028\u2029\uD83D\uDE80end"))
        .isEqualTo("id????end");
  }

  @Test
  void capsTruncatedRequestIdIncludingSuffixAt128CodePoints() {
    String sanitized = RequestIdLogSanitizer.sanitize("x".repeat(300));

    assertThat(sanitized).isEqualTo("x".repeat(125) + "...");
    assertThat(sanitized.codePointCount(0, sanitized.length())).isEqualTo(128);
  }
}
