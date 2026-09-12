package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards that the login workflow never exposes an incorrect-username vs incorrect-password
 * distinction to the frontend (credential enumeration). Both downstream outcomes must surface as
 * the single {@code err.login.credentials.invalid} public code.
 */
class LoginCredentialExposureGuardTest {
  private static final Path LOGIN_ERROR_KEY =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/LoginErrorKey.java");
  private static final Path START_WEB_MAPPER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginStartWebMapper.java");

  @Test
  void loginErrorKeyDeclaresNoIncorrectUsernameOrPasswordCode() throws Exception {
    String source = Files.readString(LOGIN_ERROR_KEY);
    assertThat(source).doesNotContain("err.login.incorrect.username");
    assertThat(source).doesNotContain("err.login.incorrect.password");
    assertThat(source).contains("err.login.credentials.invalid");
  }

  @Test
  void loginStartWebMapperEmitsCredentialsInvalidForBothIncorrectOutcomes() throws Exception {
    String source = Files.readString(START_WEB_MAPPER);
    assertThat(source).doesNotContain("LoginErrorKey.INCORRECT_PASSWORD");
    assertThat(source).doesNotContain("LoginErrorKey.INCORRECT_USERNAME");
    assertThat(source).contains("case INCORRECT_PASSWORD, INCORRECT_USERNAME ->");
    assertThat(source).contains("LoginErrorKey.CREDENTIALS_INVALID.value()");
  }
}
