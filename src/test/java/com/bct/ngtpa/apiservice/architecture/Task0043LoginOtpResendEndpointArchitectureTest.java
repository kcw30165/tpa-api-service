package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpResendEndpointArchitectureTest {
  @Test
  void endpointAndCompositionReuseExistingOtpIdempotencyLifecycle() throws Exception {
    String c =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java"));
    assertThat(c)
        .contains(
            "/resend", "ResendLoginOtpUseCase", "Idempotency-Key", "auth_flow_sid", "X-XSRF-TOKEN");
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginOtpResendApplicationConfig.java"));
    assertThat(s)
        .contains(
            "new LoginOtpResendService",
            "new IdempotentLoginOtpResendService",
            "getOtpResendTtlSeconds");
  }

  @Test
  void verificationDtosDoNotIntroduceResendSpecificDtoOrIdempotencyTrees() throws Exception {
    assertThat(
            Files.exists(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/otp/resend")))
        .isFalse();
    assertThat(
            Files.exists(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginOtpResendIdempotencyPort.java")))
        .isFalse();
  }
}
