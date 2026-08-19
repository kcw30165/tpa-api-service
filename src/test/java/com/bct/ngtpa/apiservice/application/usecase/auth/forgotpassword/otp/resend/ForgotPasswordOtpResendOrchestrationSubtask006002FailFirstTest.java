package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpResendOrchestrationSubtask006002FailFirstTest {
  @Test
  void resendOrchestrationServiceExistsAndUsesSingleReactiveMutationLockLifecycle()
      throws Exception {
    Path source =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/ResendForgotPasswordOtpService.java");

    assertThat(source).exists();

    String text = Files.readString(source);
    assertThat(text).contains("Mono.usingWhen(");
    assertThat(text).contains("loadSnapshot(");
    assertThat(text).contains("attachPendingCommitRef(");
    assertThat(text).doesNotContain("AuthFlowSessionCompletionLockPort");
    assertThat(text).doesNotContain("subscribe(");
    assertThat(text).doesNotContain("block(");
  }

  @Test
  void resendUseCasePortExists() {
    Path port =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/port/in/ResendForgotPasswordOtpUseCase.java");

    assertThat(port).exists();
  }
}
