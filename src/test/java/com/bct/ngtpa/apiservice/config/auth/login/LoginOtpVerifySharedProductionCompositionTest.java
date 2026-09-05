package com.bct.ngtpa.apiservice.config.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginOtpVerifySharedProductionCompositionTest {
  private static final Path CONFIG =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginOtpVerifyApplicationConfig.java");

  @Test
  void productionSelectsSharedCoordinatorWithConfiguredTtlAndRetainsProtectedOperation()
      throws Exception {
    String source = Files.readString(CONFIG);
    assertThat(source)
        .contains(
            "VerifyLoginOtpUseCase verifyLoginOtpUseCase(",
            "new LoginOtpVerifyService(",
            "new SharedLoginOtpVerifyService(",
            "getOtpVerifyTtlSeconds()")
        .doesNotContain("return new LoginOtpVerifyService(");
  }
}
