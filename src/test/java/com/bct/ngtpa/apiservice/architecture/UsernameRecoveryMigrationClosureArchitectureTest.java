package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Repair 025D-3 guards the closed Username Recovery session migration boundary. */
class UsernameRecoveryMigrationClosureArchitectureTest {

    @Test
    void reservationCommandHasNoTransitionalBridgeMarker() throws IOException {
        assertThat(source("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryReservationCommand.java"))
                .contains("AuthFlowSessionId")
                .doesNotContain("REMOVE_AFTER_025D", "Transitional bridge", "sessionId()");
    }

    @Test
    void idempotencyGuardUsesCanonicalWorkflowStateResource() throws IOException {
        assertThat(source("src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/RedisUsernameRecoveryIdempotencyArchitectureGuardTest.java"))
                .contains("redis/username-recovery-workflow-state/update.lua")
                .doesNotContain("redis/username-recovery-session/update.lua");
    }

    @Test
    void traceabilityNamesCanonicalWorkflowStateMapperTest() throws IOException {
        assertThat(source("docs/design/6-username-recovery/username-recovery-test-traceability-design.md"))
                .contains("RedisUsernameRecoveryWorkflowStateJsonMapperTest")
                .doesNotContain("RedisUsernameRecoverySessionJsonMapperTest");
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
