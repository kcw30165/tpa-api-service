package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginStartReplayIntegrationArchitectureTest {
  @Test
  void startLoginUsesReplayCoordinatorWithoutInfrastructureDependencies() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java"));
    assertThat(source)
        .containsIgnoringWhitespaces(
            "LoginStartReplayCoordinator",
            "replayCoordinator.commit",
            "replayCoordinator.replay",
            "replayCoordinator.markRejected",
            "replayCoordinator.markUnknown");
    assertThat(source)
        .doesNotContain(
            "idempotency.markFailed",
            "idempotency.markUnknown",
            "Redis",
            "Cipher",
            "ObjectMapper",
            "org.springframework",
            "ServerHttpResponse");
  }

  @Test
  void coordinatorCompletesOnlyAfterProtectedReplaySave() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/replay/LoginStartReplayCoordinator.java"));
    assertThat(indexOfIgnoringWhitespace(source, "replayPort.save"))
        .isLessThan(indexOfIgnoringWhitespace(source, "idempotency.complete"));
    assertThat(source)
        .containsIgnoringWhitespaces(
            "boundedTtl", "idempotency.markFailed", "idempotency.markUnknown");
  }
}
