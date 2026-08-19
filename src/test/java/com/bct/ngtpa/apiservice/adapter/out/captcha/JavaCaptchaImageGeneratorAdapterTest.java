package com.bct.ngtpa.apiservice.adapter.out.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.RecordComponent;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class JavaCaptchaImageGeneratorAdapterTest {

  private static final byte[] PNG_SIGNATURE =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  @Test
  void captchaImageGeneratorReturnsBase64Png() {
    var generator =
        new JavaCaptchaImageGeneratorAdapter(
            new SecureRandomCaptchaTextGenerator(new SecureRandom()), new SecureRandom());

    GeneratedCaptcha captcha =
        generator.generate(new CaptchaPolicy(8, "ABC123", true, 300, 5)).block();

    assertThat(captcha).isNotNull();
    assertThat(captcha.image().mimeType()).isEqualTo("image/png");
    assertThat(captcha.image().imageBase64()).isNotBlank();

    byte[] png = Base64.getDecoder().decode(captcha.image().imageBase64());
    assertThat(Arrays.copyOf(png, PNG_SIGNATURE.length)).containsExactly(PNG_SIGNATURE);
  }

  @Test
  void captchaImageUsesExpectedDimensionsForConfiguredLength() throws Exception {
    GeneratedCaptcha captcha =
        new JavaCaptchaImageGeneratorAdapter(
                new SecureRandomCaptchaTextGenerator(new SecureRandom()), new SecureRandom())
            .generate(new CaptchaPolicy(7, "WM", true, 300, 5))
            .block();

    BufferedImage image = decode(captcha);

    assertThat(image.getWidth()).isEqualTo(168);
    assertThat(image.getHeight()).isEqualTo(72);
  }

  @Test
  void captchaTextLayoutUsesFontMetricsAndBoundedCells() throws Exception {
    String source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/captcha/JavaCaptchaImageGeneratorAdapter.java"));

    assertThat(source)
        .contains("FontMetrics", "cellWidth", "glyphWidth", "fittedFont", "boundedJitter")
        .doesNotContain(
            "x += 18 + secureRandom.nextInt(10)", "baseline = 44 + secureRandom.nextInt(12)");
  }

  private BufferedImage decode(GeneratedCaptcha captcha) throws Exception {
    byte[] png = Base64.getDecoder().decode(captcha.image().imageBase64());
    return ImageIO.read(new ByteArrayInputStream(png));
  }

  @Test
  void captchaImageGeneratorDoesNotExposeImageInToString() {
    var image =
        new JavaCaptchaImageGeneratorAdapter(
                new SecureRandomCaptchaTextGenerator(new SecureRandom()), new SecureRandom())
            .generate(new CaptchaPolicy(8, "ABC123", true, 300, 5))
            .block()
            .image();

    assertThat(image.toString()).doesNotContain(image.imageBase64());
  }

  @Test
  void generatedCaptchaToStringDoesNotExposeAnswer() {
    GeneratedCaptcha captcha =
        new JavaCaptchaImageGeneratorAdapter(
                new SecureRandomCaptchaTextGenerator(new SecureRandom()), new SecureRandom())
            .generate(new CaptchaPolicy(8, "ABC123", true, 300, 5))
            .block();

    assertThat(captcha.toString()).doesNotContain(captcha.answer(), captcha.image().imageBase64());
  }

  @Test
  void captchaImageGenerationWorksInHeadlessMode() {
    String previous = System.getProperty("java.awt.headless");
    System.setProperty("java.awt.headless", "true");
    try {
      GeneratedCaptcha captcha =
          new JavaCaptchaImageGeneratorAdapter(
                  new SecureRandomCaptchaTextGenerator(new SecureRandom()), new SecureRandom())
              .generate(new CaptchaPolicy(8, "ABC123", true, 300, 5))
              .block();

      assertThat(captcha).isNotNull();
      assertThat(captcha.image().mimeType()).isEqualTo("image/png");
    } finally {
      if (previous == null) {
        System.clearProperty("java.awt.headless");
      } else {
        System.setProperty("java.awt.headless", previous);
      }
    }
  }

  @Test
  void captchaImageGeneratorDoesNotPersistOrHashAnswer() {
    assertThat(
            Arrays.stream(GeneratedCaptcha.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
        .containsExactly("answer", "image")
        .doesNotContain("answerHash", "hash", "redisKey");
  }
}
