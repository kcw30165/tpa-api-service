package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpResendRetryAfterGuardTest {

  @Test
  void resendResultAndResponseExposeRetryAfterSeconds() throws Exception {
    String result =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/ResendRegistrationOtpResult.java"));
    String response =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/dto/RegistrationOtpResendResponse.java"));
    String mapper =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/RegistrationOtpResendWebMapper.java"));

    assertThat(result).contains("Integer retryAfterSeconds");
    assertThat(response).contains("Integer retryAfterSeconds");
    assertThat(mapper)
        .contains("exposeResendFailurePayload", "COOLDOWN_ACTIVE", "retryAfterSeconds()");
  }
}
