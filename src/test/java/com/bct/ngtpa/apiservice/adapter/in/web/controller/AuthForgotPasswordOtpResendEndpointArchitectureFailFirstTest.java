package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthForgotPasswordOtpResendEndpointArchitectureFailFirstTest {
  @Test
  void forgotPasswordOtpResendEndpointAndMapperExist() throws Exception {
    Path controller =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordController.java");
    Path mapper =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordOtpResendWebMapper.java");

    assertThat(controller).exists();
    assertThat(mapper).exists();

    String controllerText = Files.readString(controller);
    String mapperText = Files.readString(mapper);

    assertThat(controllerText).contains("@PostMapping(\"/otp/resend\")");
    assertThat(controllerText).contains("@RequestMapping(\"/api/v1/auth/forgot-password\")");
    assertThat(controllerText).contains("Idempotency-Key");
    assertThat(controllerText).doesNotContain("Account-Ref");

    assertThat(mapperText).contains("WorkflowNextStep.VERIFY_OTP");
    assertThat(mapperText).contains("ForgotPasswordErrorKey.OTP_EXPIRED");
    assertThat(mapperText).doesNotContain("DOWNSTREAM_REJECTED");
    assertThat(mapperText).doesNotContain("DOWNSTREAM_ERROR");
  }

  @Test
  void forgotPasswordApplicationConfigWiresOtpResendUseCase() throws Exception {
    Path config =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java");

    String text = Files.readString(config);
    assertThat(text).contains("ResendForgotPasswordOtpUseCase");
    assertThat(text).contains("ForgotPasswordOtpResendWebMapper");
  }

  @Test
  void forgotPasswordApplicationConfigWiresDetailsOtpUseCase() throws Exception {
    Path config =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java");

    String text = Files.readString(config);
    assertThat(text).contains("ProcessForgotPasswordDetailsOtpUseCase");
    assertThat(text).contains("IdempotentForgotPasswordDetailsOtpService");
    assertThat(text)
        .contains("IdempotentForgotPasswordDetailsOtpService")
        .contains("ForgotPasswordOtpCommitStatePort")
        .doesNotContain("ForgotPasswordOtpCommitCoordinator");
    assertThat(text).contains("ForgotPasswordOtpReplayResolver");
    assertThat(text).contains("ForgotPasswordOtpCommitRetentionPolicy");
  }
}
