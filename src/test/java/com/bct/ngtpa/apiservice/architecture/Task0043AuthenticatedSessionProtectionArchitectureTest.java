package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionProtectionArchitectureTest {
  @Test
  void protectorIsInfrastructureOwnedAndImplementsApplicationPort() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/session/AesGcmAuthenticatedSessionProtector.java"));
    assertThat(s)
        .contains(
            "implements AuthenticatedSessionProtectorPort", "AES/GCM/NoPadding", "SecureRandom")
        .doesNotContain("org.springframework", "Redis", "WebClient");
  }

  @Test
  void persistedAdapterStillHandlesProtectedEnvelopeOnly() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthenticatedSessionAdapter.java"));
    assertThat(s)
        .contains("ProtectedAuthenticatedSession")
        .doesNotContain("AuthenticatedSessionProtectorPort", "ActorContext", "policyNo");
  }

  @Test
  void applicationLayerHasNoCryptoImplementationDependency() throws Exception {
    String all =
        Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/application"))
            .filter(Files::isRegularFile)
            .map(
                p -> {
                  try {
                    return Files.readString(p);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .reduce("", String::concat);
    assertThat(all)
        .doesNotContain(
            "AesGcmAuthenticatedSessionProtector", "javax.crypto", "Cipher.getInstance");
  }
}
