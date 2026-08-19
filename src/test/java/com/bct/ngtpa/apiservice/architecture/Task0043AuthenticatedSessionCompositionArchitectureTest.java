package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionCompositionArchitectureTest {
  @Test
  void springCompositionLivesOutsideApplicationAndInfrastructureImplementation() throws Exception {
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java"));
    assertThat(config)
        .contains("@Configuration", "AuthenticatedSessionPort", "AuthenticatedSessionProtectorPort")
        .contains("RedisAuthenticatedSessionAdapter", "AesGcmAuthenticatedSessionProtector")
        .doesNotContain("ReactiveStringRedisTemplate", "RedisConnectionFactory");
  }

  @Test
  void authenticatedSessionKeyIsDedicatedAndNotHardcodedInJava() throws Exception {
    String all =
        Files.walk(Path.of("src/main/java"))
            .filter(Files::isRegularFile)
            .map(
                path -> {
                  try {
                    return Files.readString(path);
                  } catch (Exception exception) {
                    throw new RuntimeException(exception);
                  }
                })
            .reduce("", String::concat);
    assertThat(all)
        .contains("auth-workflows.login.session.protection")
        .doesNotContain("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
  }

  @Test
  void applicationLayerDoesNotOwnSpringOrCryptoComposition() throws Exception {
    String all =
        Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/application"))
            .filter(Files::isRegularFile)
            .map(
                path -> {
                  try {
                    return Files.readString(path);
                  } catch (Exception exception) {
                    throw new RuntimeException(exception);
                  }
                })
            .reduce("", String::concat);
    assertThat(all)
        .doesNotContain(
            "org.springframework.context.annotation",
            "AesGcmAuthenticatedSessionProtector",
            "RedisAuthenticatedSessionAdapter");
  }
}
