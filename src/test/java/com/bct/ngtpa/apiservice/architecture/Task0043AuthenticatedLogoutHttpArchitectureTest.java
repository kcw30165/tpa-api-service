package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedLogoutHttpArchitectureTest {
  @Test
  void endpointUsesPortalCookieCsrfAndThinUseCaseBoundary() throws Exception {
    String source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/LogoutController.java"));
    org.assertj.core.api.Assertions.assertThat(source)
        .contains(
            "@PostMapping(\"/logout\")",
            "AuthenticatedSessionCookieWriter",
            "ValidateAuthenticatedSessionUseCase",
            "AuthenticatedLogoutUseCase",
            "AuthenticatedLogoutCommand",
            "sessionCookies.cookieName()",
            "sessionCookies.clear(response)",
            "RequestHeaderContextKeys.XSRF_TOKEN_HEADER")
        .doesNotContain("AUTHENTICATED_SESSION_HEADER", "X-Authenticated-Session-Id");
  }

  @Test
  void publicMapperDoesNotExposeLogoutExistenceOrSessionIdentity() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/AuthenticatedLogoutWebMapper.java"));
    assertThat(source)
        .doesNotContain("AuthenticatedSessionId", "sessionId", "actorContext", "accounts");
  }
}
