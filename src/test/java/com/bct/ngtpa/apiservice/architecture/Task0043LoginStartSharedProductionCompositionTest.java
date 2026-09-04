package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginStartSharedProductionCompositionTest {
  @Test
  void productionSelectsSharedServiceWithConfiguredTtlAndRetainsRollbackPath() throws Exception {
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginApplicationConfig.java"));
    String compact = config.replaceAll("\s+", "");
    assertThat(compact)
        .contains("returnnewSharedStartLoginService(")
        .contains("Duration.ofSeconds(workflows.getLogin().getIdempotency().getStartTtlSeconds())")
        .doesNotContain("returnnewStartLoginService(");
    assertThat(
            Files.exists(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java")))
        .isTrue();
    assertThat(config)
        .contains(
            "LoginCredentialIdempotencyCoordinator",
            "LoginCredentialRequestHasher",
            "LoginStartReplayCoordinator");
  }
}
