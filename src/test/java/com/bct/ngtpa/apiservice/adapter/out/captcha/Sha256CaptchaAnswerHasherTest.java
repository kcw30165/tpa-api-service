package com.bct.ngtpa.apiservice.adapter.out.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Sha256CaptchaAnswerHasherTest {

  private final Sha256CaptchaAnswerHasher hasher = new Sha256CaptchaAnswerHasher();

  @Test
  void captchaAnswerHasherProducesSha256Hex() {
    CaptchaAnswerHash hash = hasher.hash("AbC123x", true);

    assertThat(hash.value()).matches("^[a-f0-9]{64}$");
    assertThat(hash.toString()).doesNotContain("AbC123x", hash.value());
  }

  @Test
  void captchaAnswerHasherMatchesSameAnswerWhenCaseSensitive() {
    CaptchaAnswerHash hash = hasher.hash("AbC123x", true);

    assertThat(hasher.matches("AbC123x", hash, true)).isTrue();
    assertThat(hasher.matches("abc123x", hash, true)).isFalse();
  }

  @Test
  void captchaAnswerHasherMatchesIgnoringCaseWhenCaseInsensitive() {
    CaptchaAnswerHash hash = hasher.hash("AbC123x", false);

    assertThat(hasher.matches("AbC123x", hash, false)).isTrue();
    assertThat(hasher.matches("abc123x", hash, false)).isTrue();
    assertThat(hasher.matches("ABC123X", hash, false)).isTrue();
  }

  @Test
  void captchaAnswerHasherTrimsOuterWhitespace() {
    CaptchaAnswerHash hash = hasher.hash("AbC123x", true);

    assertThat(hasher.matches(" AbC123x ", hash, true)).isTrue();
  }

  @Test
  void captchaAnswerHasherRejectsBlankAnswerWithoutEchoing() {
    for (String value : new String[] {null, "", "   "}) {
      assertThatThrownBy(() -> hasher.hash(value, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Captcha answer is invalid")
          .satisfies(
              ex -> {
                if (value != null && !value.isBlank()) {
                  assertThat(ex.getMessage()).doesNotContain(value);
                }
              });
    }
  }

  @Test
  void captchaAnswerHasherUsesConstantTimeHashComparison() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/captcha/Sha256CaptchaAnswerHasher.java"));
    assertThat(source).contains("MessageDigest.isEqual");
  }
}
