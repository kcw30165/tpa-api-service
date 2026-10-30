package com.bct.ngtpa.apiservice.adapter.out.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonOperationPayloadCanonicalizerTest {
  private final JsonOperationPayloadCanonicalizer canonicalizer =
      new JsonOperationPayloadCanonicalizer();

  @Test
  void ignoresRecursiveObjectOrderEscapesAndDecimalScaleWithoutLosingPrecision() {
    var left =
        canonicalizer.digest(
            "{\"fields\":{\"z\":[1.0,{\"b\":\"\\u0061\",\"a\":-0}],\"a\":12345678901234567890.12345678901234567890},"
                + "\"applyToAllAccounts\":false,\"formVersion\":\"v\"}");
    var right =
        canonicalizer.digest(
            "{\"formVersion\":\"v\",\"applyToAllAccounts\":false,"
                + "\"fields\":{\"a\":12345678901234567890.123456789012345678900,\"z\":[1,{\"a\":0.0,\"b\":\"a\"}]}}");
    assertThat(left.matches(right)).isTrue();
    assertThat(left.version()).isEqualTo(1);
    assertThat(left.toString()).contains("<redacted>").doesNotContain(left.sha256());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"fields\":{\"x\":\"1\"}}",
        "{\"fields\":{\"x\":true}}",
        "{\"fields\":{\"x\":null}}",
        "{\"fields\":{}}",
        "{\"fields\":{\"x\":[1]}}",
        "{\"fields\":{\"x\":{\"v\":1}}}",
        "{\"fields\":{\"x\":1.00000000000000000001}}",
        "{\"fields\":{\"x\":1},\"unknown\":null}",
        "{\"fields\":{\"x\":1},\"applyToAllAccounts\":true}",
        "{\"fields\":{\"x\":1},\"formVersion\":\"changed\"}"
      })
  void bindsEveryValueTypeAndPresence(String changed) {
    assertThat(
            canonicalizer.digest("{\"fields\":{\"x\":1}}").matches(canonicalizer.digest(changed)))
        .isFalse();
  }

  @Test
  void preservesArrayOrderWhitespaceCaseAndUnicodeWithoutNormalization() {
    unequal("{\"x\":[1,2]}", "{\"x\":[2,1]}");
    unequal("{\"x\":\" A \"}", "{\"x\":\"A\"}");
    unequal("{\"x\":\"a\"}", "{\"x\":\"A\"}");
    unequal("{\"x\":\"\\u00e9\"}", "{\"x\":\"e\\u0301\"}");
    unequal("{\"x\":\"\\ud800\"}", "{\"x\":\"?\"}");
    unequal("{\"x\":\"\\ud800\"}", "{\"x\":\"\\ud801\"}");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"x\":1,\"x\":2}",
        "{\"nested\":{\"x\":1,\"x\":2}}",
        "{\"x\":[{\"a\":1,\"\\u0061\":2}]}",
        "{} {}",
        "{} trailing",
        "{\"x\":NaN}",
        "{\"x\":Infinity}",
        "{\"x\":01}",
        "{\"x\":1,}",
        "{\"x\":",
        "",
        "[]",
        "null",
        "true"
      })
  void rejectsAmbiguousOrNonJsonInputWithoutEchoingIt(String json) {
    assertThatThrownBy(() -> canonicalizer.digest(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid operation payload")
        .hasNoCause();
  }

  @Test
  void versionIsPartOfComparisonAndNullIsNeverEqual() {
    var digest = canonicalizer.digest("{}");
    assertThat(digest.matches(new PayloadDigest(2, digest.sha256()))).isFalse();
    assertThat(digest.matches(null)).isFalse();
    var sha256 = digest.sha256();
    assertThatThrownBy(() -> canonicalizer.digest(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PayloadDigest(0, sha256))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PayloadDigest(1, "bad"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fixedVersionOneVector() {
    assertThat(canonicalizer.digest("{}").sha256())
        .isEqualTo("1e08b461b9803ab3c74e8791fb16c2b9b490b3a8754bbcb50a59b98d7c0f662b");
  }

  private void unequal(String left, String right) {
    assertThat(canonicalizer.digest(left).matches(canonicalizer.digest(right))).isFalse();
  }
}
