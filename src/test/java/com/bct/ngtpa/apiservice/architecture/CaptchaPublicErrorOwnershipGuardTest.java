package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CaptchaPublicErrorOwnershipGuardTest {
  private static final List<Path> STANDALONE =
      List.of(
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaHttpResponses.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/mapper/CaptchaWebMapper.java"));

  @Test
  void standaloneCaptchaProductionContainsNoRegistrationOwnedErrorOrWebDependency()
      throws Exception {
    String source = readAll(STANDALONE);
    assertThat(source)
        .doesNotContain(
            "err.registration.",
            "RegistrationErrorKey",
            "auth.registration",
            "RegistrationHttpResponses");
  }

  @Test
  void emittedCaptchaOwnedErrorHasCanonicalDefinitionAndNoBackendMessage() throws Exception {
    String mapper = Files.readString(STANDALONE.get(2));
    String errorCodes =
        Files.readString(
            Path.of("src/main/java/com/bct/ngtpa/apiservice/shared/error/ErrorCodes.java"));
    String yaml =
        Files.readString(Path.of("src/main/resources/application-registration-errors.yml"));
    assertThat(mapper).containsIgnoringWhitespaces("CAPTCHA_VERIFICATION_INVALID");
    assertThat(errorCodes).containsIgnoringWhitespaces("err.captcha.verification.invalid");
    assertThat(yaml)
        .containsIgnoringWhitespaces(
            "code: err.captcha.verification.invalid", "targets:\n        - captchaAnswer")
        .doesNotContain("message:");
  }

  @Test
  void registrationOwnedCaptchaErrorsRemainConfigured() throws Exception {
    String yaml =
        Files.readString(Path.of("src/main/resources/application-registration-errors.yml"));
    assertThat(yaml)
        .containsIgnoringWhitespaces(
            "err.registration.captcha.invalid", "err.registration.captcha.required");
  }

  private static String readAll(List<Path> paths) throws Exception {
    StringBuilder value = new StringBuilder();
    for (Path path : paths) value.append(Files.readString(path)).append('\n');
    return value.toString();
  }
}
