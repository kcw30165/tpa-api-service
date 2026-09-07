package com.bct.ngtpa.apiservice.config.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpVerifyCompositionArchitectureTest {
  private static final Path CONFIG =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java");

  @Test
  void productionComposesExactlyOneSharedOtpVerifyUseCase() throws Exception {
    String source = Files.readString(CONFIG);
    assertThat(source)
        .containsOnlyOnce("public VerifyForgotPasswordOtpUseCase verifyForgotPasswordOtpUseCase(")
        .containsOnlyOnce(".IdempotentVerifyForgotPasswordOtpService(")
        .containsOnlyOnce(".ForgotPasswordOtpVerifyProtectedOperation(")
        .containsOnlyOnce(".ForgotPasswordOtpVerifyExecutionBridge(")
        .containsOnlyOnce(".ForgotPasswordOtpVerifyReplayCodec(")
        .contains("IdempotencyExecutor idempotencyExecutor");
  }

  @Test
  void otpVerifyBeanNoLongerReceivesLegacyLifecyclePort() throws Exception {
    String source = Files.readString(CONFIG);
    int start =
        source.indexOf("public VerifyForgotPasswordOtpUseCase verifyForgotPasswordOtpUseCase(");
    int end = source.indexOf("\n  }\n}", start);
    assertThat(start).isGreaterThanOrEqualTo(0);
    assertThat(end).isGreaterThan(start);
    assertThat(source.substring(start, end)).doesNotContain("ForgotPasswordIdempotencyPort");
  }
}
