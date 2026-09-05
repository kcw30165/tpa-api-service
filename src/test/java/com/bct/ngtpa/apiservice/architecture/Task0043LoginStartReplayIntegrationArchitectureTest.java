package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.indexOfIgnoringWhitespace;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginStartReplayIntegrationArchitectureTest {

  @Test
  void activeSharedPathUsesProtectedOperationWithoutInfrastructureDependencies() throws Exception {
    String shared =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/SharedStartLoginService.java");

    assertThat(shared)
        .contains(
            "LoginStartProtectedOperation",
            "LoginStartProtectedReplayCommitter",
            "LoginStartSharedExecutionBridge",
            "IdempotencyExecutor")
        .doesNotContain(
            "import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginService;",
            "new StartLoginService(",
            "LoginStartReplayCoordinator",
            "Redis",
            "Cipher",
            "ObjectMapper",
            "org.springframework",
            "ServerHttpResponse");
  }

  @Test
  void activeSharedPathPersistsProtectedReplayBeforeResponseCapture() throws Exception {
    String committer =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/replay/LoginStartProtectedReplayCommitter.java");
    String shared =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/SharedStartLoginService.java");

    assertThat(committer)
        .contains("boundedTtl", "replayPort.save")
        .doesNotContain("IdempotencyExecutor", "org.springframework", "Redis");
    assertThat(indexOfIgnoringWhitespace(shared, "committer.commit"))
        .isLessThan(indexOfIgnoringWhitespace(shared, "responses.toTransientResponse"));
    assertThat(shared)
        .doesNotContain(
            "LoginStartReplayCoordinator",
            "LoginCredentialIdempotencyCoordinator",
            "replayPort.save");
  }

  private static String source(String path) throws Exception {
    return Files.readString(Path.of(path));
  }
}
