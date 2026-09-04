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
  void activeSharedPathBoundsAndPersistsProtectedReplayBeforeResponseCapture() throws Exception {
    String committer =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/replay/LoginStartProtectedReplayCommitter.java"));
    String shared =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/SharedStartLoginService.java"));
    String legacyCoordinator =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/replay/LoginStartReplayCoordinator.java"));

    assertThat(committer)
        .contains("boundedTtl", "replayPort.save")
        .doesNotContain("IdempotencyExecutor", "org.springframework", "Redis");
    assertThat(indexOfIgnoringWhitespace(shared, "committer.commit"))
        .isLessThan(indexOfIgnoringWhitespace(shared, "responses.toTransientResponse"));
    assertThat(legacyCoordinator)
        .containsIgnoringWhitespaces("protectedReplayCommitter.commit", "idempotency.complete")
        .doesNotContain("replayPort.save", "IdempotencyExecutor", "SharedStartLoginService");
  }
}
