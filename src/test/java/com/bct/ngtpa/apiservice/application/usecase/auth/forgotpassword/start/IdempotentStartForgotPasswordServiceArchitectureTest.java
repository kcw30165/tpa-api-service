package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IdempotentStartForgotPasswordServiceArchitectureTest {
  @Test
  void coordinatorUsesOnlySharedLifecycleAndFeatureIntegrationPolicy() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/start/IdempotentStartForgotPasswordService.java"));
    assertThat(source)
        .contains(
            "IdempotencyExecutor",
            "ForgotPasswordIdempotencyIntegrationPolicy",
            "ForgotPasswordStartProtectedOperation",
            "ForgotPasswordStartExecutionBridge",
            "IdempotencyFailureClassifiers.possibleSideEffect()")
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            "adapter.out.redis",
            "adapter.in.web");
  }
}
