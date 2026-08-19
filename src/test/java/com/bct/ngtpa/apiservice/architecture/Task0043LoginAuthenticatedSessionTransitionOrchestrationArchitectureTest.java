package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginAuthenticatedSessionTransitionOrchestrationArchitectureTest {
  @Test
  void serviceUsesNarrowLifecycleAndCleanupCapabilities() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java"));
    assertThat(s)
        .contains(
            "CreateAuthenticatedSessionUseCase",
            "DeleteAuthFlowSessionPort",
            "RevokeAuthenticatedSessionUseCase",
            "LoginAuthenticatedSessionAccountMapper")
        .doesNotContain(
            "Redis",
            "ObjectMapper",
            "ServerHttp",
            "Cookie",
            "import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort",
            "private final AuthFlowSessionPort ",
            "AuthFlowSessionPort sessions");
  }

  @Test
  void createPrecedesCleanupAndCleanupFailureRevokes() throws Exception {
    String s =
        Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java"))
            .replaceAll("\\s+", "");
    assertThat(s).contains("create.create(", "cleanup.delete(", "revoke.revoke(");
    assertThat(s.indexOf("create.create(")).isLessThan(s.indexOf("cleanup.delete("));
  }

  @Test
  void webAndOtpIntegrationRemainDeferred() {
    assertThat(
            Files.exists(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/config/AuthenticatedSessionCookieWriter.java")))
        .isFalse();
  }
}
