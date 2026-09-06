package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordStartProtectedOperationArchitectureTest {
  @Test
  void protectedOperationOwnsNoIdempotencyOrOuterLayerDependency() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/start/ForgotPasswordStartProtectedOperation.java"));
    assertThat(source)
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "IdempotencyExecutor",
            "IdempotentExecutionCommand",
            "adapter.out.redis",
            "adapter.in.web",
            "markCompleted(",
            "markFailed(",
            "startProcessing(");
    assertThat(source).contains("Mono<StartForgotPasswordResult> execute(");
  }
}
