package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionPersistenceContractArchitectureTest {
  private static final Path MAIN = Path.of("src/main/java");

  @Test
  void protectedWireContractUsesHybridLifecycleEnvelope() throws Exception {
    String source =
        Files.readString(
            MAIN.resolve(
                "com/bct/ngtpa/apiservice/application/dto/session/authenticated/ProtectedAuthenticatedSession.java"));

    assertThat(source)
        .contains(
            "authenticatedSessionId",
            "AuthenticatedSessionStatus status",
            "Data data",
            "Instant createdAt",
            "Instant updatedAt",
            "Instant idleExpiresAt",
            "Instant absoluteExpiresAt",
            "authenticatedContext.ciphertext",
            "authenticatedContext.algorithm",
            "authenticatedContext.nonce",
            "authenticatedContext.authenticationTag",
            "authenticatedContext.keyVersion");
    assertThat(source)
        .doesNotContain(
            "ActorContext actorContext",
            "List<AuthenticatedSessionAccount>",
            "String csrfTokenHash");
  }

  @Test
  void persistencePortRemainsFrameworkAndInfrastructureNeutral() throws Exception {
    String source =
        Files.readString(
            MAIN.resolve(
                "com/bct/ngtpa/apiservice/application/port/out/AuthenticatedSessionPort.java"));
    assertThat(source)
        .contains(
            "store(StoreAuthenticatedSessionCommand",
            "load(LoadAuthenticatedSessionCommand",
            "touch(TouchAuthenticatedSessionCommand",
            "revoke(RevokeAuthenticatedSessionCommand",
            "delete(DeleteAuthenticatedSessionCommand");
    assertThat(source)
        .doesNotContain(
            "org.springframework", "CachePort", "ObjectMapper", "RedisAuthenticatedSessionAdapter");
  }

  @Test
  void persistenceCommandsContinueToCarryCallerOwnedPositiveTtl() throws Exception {
    String store =
        Files.readString(
            MAIN.resolve(
                "com/bct/ngtpa/apiservice/application/dto/session/authenticated/StoreAuthenticatedSessionCommand.java"));
    String touch =
        Files.readString(
            MAIN.resolve(
                "com/bct/ngtpa/apiservice/application/dto/session/authenticated/TouchAuthenticatedSessionCommand.java"));
    assertThat(store).contains("Duration ttl", "ttl.isZero()", "ttl.isNegative()");
    assertThat(touch).contains("Duration ttl", "ttl.isZero()", "ttl.isNegative()");
  }
}
