package com.bct.ngtpa.apiservice.shared.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentifierNormalizerTest {
  @Test
  void normalizeTrimsAndNullsBlanks() {
    assertThat(IdentifierNormalizer.normalize("  JPJPM02 ")).isEqualTo("JPJPM02");
    assertThat(IdentifierNormalizer.normalize("   ")).isNull();
    assertThat(IdentifierNormalizer.normalize(null)).isNull();
  }

  @Test
  void equalsNormalizedIgnoresCaseAndWhitespace() {
    assertThat(IdentifierNormalizer.equalsNormalized("JPJPM02", " jpjpm02 ")).isTrue();
    assertThat(IdentifierNormalizer.equalsNormalized("abc", "ABC")).isTrue();
  }

  @Test
  void equalsNormalizedRejectsMismatchAndNulls() {
    assertThat(IdentifierNormalizer.equalsNormalized("abc", "abd")).isFalse();
    assertThat(IdentifierNormalizer.equalsNormalized(null, null)).isFalse();
    assertThat(IdentifierNormalizer.equalsNormalized("abc", null)).isFalse();
    assertThat(IdentifierNormalizer.equalsNormalized(null, "abc")).isFalse();
    assertThat(IdentifierNormalizer.equalsNormalized(" ", "abc")).isFalse();
  }
}
