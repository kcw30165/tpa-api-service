package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Repair 025A legacy-usage guard.
 *
 * <p>Under the Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR},
 * {@code AuthFlowSessionId} is the sole canonical identifier. {@code UsernameRecoverySessionId}
 * remains temporarily only because later committed callers and persistence files still reference
 * it; Repair 025D owns its final removal. This guard prevents NEW production usages of the legacy
 * identifier outside the explicit committed legacy allowlist.
 *
 * <p>The allowlist is the exact production baseline of source files referencing the identifier,
 * derived from the audit's whole-tree search (audit evidence 004 Q2, 14 production files): the
 * legacy Redis adapter/key-factory/record, the legacy session port and mutation command, the
 * committed Request/Start/Cancel services and replay codecs, the reservation command/service
 * (transitional bridge), the legacy domain aggregate, and the identifier type itself. The scan is
 * source-level so it also catches pass-through usage (for example the reservation service, which
 * forwards {@code sessionId()} results without declaring the type). The assertion is exact
 * (containsExactlyInAnyOrder), so an out-of-allowlist file OR a missing/extra allowlist entry both
 * fail the guard.
 */
class UsernameRecoveryLegacySessionUsageGuardTest {

    private static final String LEGACY_IDENTIFIER = "UsernameRecoverySessionId";

    private static final Set<String> ALLOWED_LEGACY_USERS = Set.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/"
                    + "RedisUsernameRecoverySessionAdapter.java",
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/"
                    + "RedisUsernameRecoverySessionKeyFactory.java",
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/"
                    + "RedisUsernameRecoverySessionRecord.java",
            "src/main/java/com/bct/ngtpa/apiservice/application/port/out/"
                    + "UsernameRecoverySessionMutationCommand.java",
            "src/main/java/com/bct/ngtpa/apiservice/application/port/out/"
                    + "UsernameRecoverySessionPort.java",
            "src/main/java/com/bct/ngtpa/apiservice/domain/model/UsernameRecoverySession.java",
            "src/main/java/com/bct/ngtpa/apiservice/domain/model/UsernameRecoverySessionId.java");

    @Test
    void noNewProductionUsagesOfLegacyUsernameRecoverySessionIdOutsideCommittedAllowlist()
            throws IOException {
        Path root = Path.of("src/main/java");
        Set<String> actualUsers = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.contains(LEGACY_IDENTIFIER)) {
                    actualUsers.add(path.toString().replace('\\', '/'));
                }
            }
        }

        assertThat(actualUsers)
                .as("Production source files referencing the legacy UsernameRecoverySessionId must "
                        + "be exactly the committed legacy allowlist (Repair 025D removes it)")
                .containsExactlyInAnyOrderElementsOf(ALLOWED_LEGACY_USERS);
    }
}
