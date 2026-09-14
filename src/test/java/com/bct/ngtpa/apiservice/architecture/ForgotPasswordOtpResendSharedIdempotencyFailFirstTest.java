package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpResendSharedIdempotencyFailFirstTest {
  private static final Path RESEND_PACKAGE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend");
  private static final Path INTEGRATION_PACKAGE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/idempotency/otpresend");

  @Test
  void sharedCoordinatorDelegatesLifecycleToRepositoryExecutor() throws Exception {
    Path source = RESEND_PACKAGE.resolve("IdempotentResendForgotPasswordOtpService.java");
    assertThat(source).exists();
    String text = Files.readString(source);
    assertThat(text)
        .contains(
            "IdempotencyExecutor",
            "IdempotentExecutionCommand",
            "ForgotPasswordIdempotencyAction.OTP_RESEND")
        .doesNotContain("ForgotPasswordIdempotencyPort", "ForgotPasswordOtpCommitCoordinator");
  }

  @Test
  void protectedOperationOwnsResendMutationWithoutLegacyLifecyclePort() throws Exception {
    Path source = RESEND_PACKAGE.resolve("ForgotPasswordOtpResendProtectedOperation.java");
    assertThat(source).exists();
    String text = Files.readString(source);
    assertThat(text)
        .contains(
            "Mono.usingWhen(",
            "loadSnapshot(",
            "MessageDeliveryPort",
            "ForgotPasswordOtpCommitStatePort")
        .doesNotContain(
            "ForgotPasswordIdempotencyPort", "attachPendingCommitRef(", "subscribe(", "block(");
  }

  @Test
  void actionBridgeAndCodecPreserveSafeAndProtectedReplaySeparation() throws Exception {
    Path bridge = INTEGRATION_PACKAGE.resolve("ForgotPasswordOtpResendExecutionBridge.java");
    Path codec = INTEGRATION_PACKAGE.resolve("ForgotPasswordOtpResendReplayCodec.java");
    Path resolver = INTEGRATION_PACKAGE.resolve("ForgotPasswordOtpResendProtectedReplayPort.java");
    assertThat(bridge).exists();
    assertThat(codec).exists();
    assertThat(resolver).exists();
    String codecText = Files.readString(codec);
    assertThat(codecText)
        .contains(
            "ActionTaggedIdempotencyResponseEnvelope", "ProtectedReplayReference", "OTP_RESEND")
        .doesNotContain("protectedDestination", "rawCode", "csrfToken");
  }

  @Test
  void productionCompositionUsesSharedCoordinatorAfterParityIsProven() throws Exception {
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java"));
    assertThat(config)
        .contains(".IdempotentResendForgotPasswordOtpService(")
        .doesNotContain("new ResendForgotPasswordOtpService(");
  }
}
