package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first boundary for the idempotency-neutral Username Recovery START protected operation. */
class UsernameRecoveryStartProtectedOperationFailFirstTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryStartProtectedOperation.java");
  private static final Path CONFIG =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java");

  @Test
  void protectedOperationExistsAndOwnsTheAcquiredWorkflowWithoutLifecyclePersistence()
      throws Exception {
    assertThat(OPERATION).exists();
    String source = Files.readString(OPERATION);
    String compact = source.replaceAll("\\s+", "");
    assertThat(source)
        .contains(
            "class UsernameRecoveryStartProtectedOperation",
            "RegistrationCaptchaGateService",
            "UsernameRecoveryLookupPort",
            "AuthFlowSessionPort",
            "UsernameRecoveryAuthFlowContextBinding",
            "CsrfTokenGenerator",
            "CsrfTokenHasher")
        .doesNotContain(
            "IdempotencyExecutor",
            "IdempotentExecutionCommand",
            "ReplaySnapshot",
            "adapter.out.redis",
            "adapter.in.web",
            "reserve(",
            "resolve(",
            "complete(",
            "recordUnknownOutcome(");
    assertThat(compact)
        .contains("captchaGateService.consume(", "lookupPort.search(", "createRevisioned(");
    assertThat(compact.indexOf("captchaGateService.consume("))
        .isLessThan(compact.indexOf("lookupPort.search("));
    assertThat(compact.indexOf("lookupPort.search("))
        .isLessThan(compact.indexOf("createRevisioned("));
  }

  @Test
  void productionUsesSharedCoordinatorAndComposedProtectedOperationAfterSwitch() throws Exception {
    String config = Files.readString(CONFIG);
    assertThat(config)
        .contains("new IdempotentStartUsernameRecoveryService(")
        .doesNotContain("new StartUsernameRecoveryService(")
        .contains("new UsernameRecoveryStartProtectedOperation(");
  }
}
