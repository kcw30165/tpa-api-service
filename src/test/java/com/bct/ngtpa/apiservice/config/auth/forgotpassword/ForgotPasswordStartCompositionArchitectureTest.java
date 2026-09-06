package com.bct.ngtpa.apiservice.config.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordStartCompositionArchitectureTest {
  private static final Path CONFIG =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java");

  @Test
  void productionComposesExactlyOneSharedLifecycleStartUseCase() throws Exception {
    String source = Files.readString(CONFIG);
    assertThat(source)
        .containsOnlyOnce("public StartForgotPasswordUseCase startForgotPasswordUseCase(")
        .containsOnlyOnce("new IdempotentStartForgotPasswordService(")
        .contains("IdempotencyExecutor idempotencyExecutor")
        .contains("ForgotPasswordStartProtectedReplayPort protectedReplay")
        .contains("new ForgotPasswordStartProtectedOperation(")
        .contains("new ForgotPasswordStartExecutionBridge(")
        .contains("new ForgotPasswordStartReplayCodec(")
        .contains("new ForgotPasswordIdempotencyIntegrationPolicy(")
        .doesNotContain("new StartForgotPasswordService(");
  }

  @Test
  void startBeanNoLongerReceivesLegacyForgotPasswordIdempotencyPort() throws Exception {
    String source = Files.readString(CONFIG);
    int start = source.indexOf("public StartForgotPasswordUseCase startForgotPasswordUseCase(");
    int end = source.indexOf("public com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase", start);
    assertThat(start).isGreaterThanOrEqualTo(0);
    assertThat(end).isGreaterThan(start);
    assertThat(source.substring(start, end))
        .doesNotContain("ForgotPasswordIdempotencyPort", "StartForgotPasswordService");
  }
}
