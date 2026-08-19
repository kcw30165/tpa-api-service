package com.bct.ngtpa.apiservice.adapter.out.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class SecureRandomCaptchaTextGeneratorTest {

  @Test
  void captchaTextGeneratorUsesConfiguredLength() {
    var generator = new SecureRandomCaptchaTextGenerator(new SecureRandom());

    String answer = generator.generate(new CaptchaPolicy(8, "ABC123", true, 300, 5));

    assertThat(answer).hasSize(8);
  }

  @Test
  void captchaTextGeneratorUsesConfiguredAlphabet() {
    var generator = new SecureRandomCaptchaTextGenerator(new SecureRandom());

    for (int index = 0; index < 20; index++) {
      assertThat(generator.generate(new CaptchaPolicy(12, "XYZ", true, 300, 5)))
          .matches("[XYZ]{12}");
    }
  }

  @Test
  void captchaTextGeneratorUsesSecureRandom() throws Exception {
    Field field = SecureRandomCaptchaTextGenerator.class.getDeclaredField("secureRandom");

    assertThat(field.getType()).isEqualTo(SecureRandom.class);
  }
}
