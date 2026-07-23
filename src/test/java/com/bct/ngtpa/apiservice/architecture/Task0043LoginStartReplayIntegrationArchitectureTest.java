package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginStartReplayIntegrationArchitectureTest {
    @Test
    void startLoginUsesReplayCoordinatorWithoutInfrastructureDependencies() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java"));
        assertThat(source).contains(
                "LoginStartReplayCoordinator",
                "replayCoordinator.commit",
                "replayCoordinator.replay",
                "idempotency.markFailed",
                "idempotency.markUnknown");
        assertThat(source).doesNotContain(
                "Redis", "Cipher", "ObjectMapper", "org.springframework", "ServerHttpResponse");
    }

    @Test
    void coordinatorCompletesOnlyAfterProtectedReplaySave() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/replay/LoginStartReplayCoordinator.java"));
        assertThat(source.indexOf("replayPort.save")).isLessThan(source.indexOf("idempotency.complete"));
        assertThat(source).contains("boundedTtl", "markUnknown");
    }
}
