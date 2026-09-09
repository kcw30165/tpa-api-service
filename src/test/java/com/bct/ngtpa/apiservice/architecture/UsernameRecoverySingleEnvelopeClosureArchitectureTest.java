package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Final closure guard for the Username Recovery single-envelope migration. */
class UsernameRecoverySingleEnvelopeClosureArchitectureTest {
  private static final Path MAIN = Path.of("src/main");
  private static final List<Path> SERVICES =
      List.of(
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryStartProtectedOperation.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryRequestProtectedOperation.java"),
          Path.of(
              "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/CancelUsernameRecoveryService.java"));

  @Test
  void productionContainsNoWorkflowStateSidecarTypeKeyOrResource() throws Exception {
    try (var files = Files.walk(MAIN)) {
      var violations =
          files
              .filter(Files::isRegularFile)
              .filter(
                  path -> {
                    try {
                      String content = Files.readString(path);
                      return path.getFileName().toString().contains("UsernameRecoveryWorkflowState")
                          || content.contains("username-recovery:workflow-state")
                          || content.contains("username-recovery-workflow-state")
                          || content.contains("UsernameRecoveryWorkflowStatePort");
                    } catch (Exception error) {
                      throw new IllegalStateException(error);
                    }
                  })
              .toList();
      assertThat(violations).isEmpty();
    }
  }

  @Test
  void startRequestAndCancelUseAuthFlowEnvelopeAndProtectedContextOnly() throws Exception {
    for (Path service : SERVICES) {
      String source = Files.readString(service);
      assertThat(source)
          .contains("AuthFlowSessionPort", "UsernameRecoveryAuthFlowContextBinding")
          .doesNotContain("WorkflowStatePort", "workflowStatePort", "sidecar");
    }
    assertThat(Files.readString(SERVICES.get(0))).contains("createRevisioned");
    assertThat(Files.readString(SERVICES.get(1))).contains("updateRevisioned");
    assertThat(Files.readString(SERVICES.get(2))).contains("cancelRevisioned");
  }

  @Test
  void configurationKeepsContextAndIdempotencyButNoSidecarBeans() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java"));
    assertThat(source)
        .contains(
            "UsernameRecoveryAuthFlowContextProtectionProperties",
            "UsernameRecoveryAuthFlowContextProtectorPort",
            "UsernameRecoveryAuthFlowContextBinding",
            "UsernameRecoveryIdempotencyPort")
        .doesNotContain(
            "UsernameRecoveryWorkflowStatePort",
            "RedisUsernameRecoveryWorkflowState",
            "AesGcmUsernameRecoveryWorkflowState");
  }
}
