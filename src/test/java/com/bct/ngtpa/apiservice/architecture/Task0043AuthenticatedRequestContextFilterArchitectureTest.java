package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedRequestContextFilterArchitectureTest {
  @Test
  void filterUsesPortalCookieAuthenticatedCsrfAndTwoArgumentResolution() throws Exception {
    String source =
        java.nio.file.Files.readString(
                java.nio.file.Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"))
            .replaceAll("\\s+", "");
    org.assertj.core.api.Assertions.assertThat(source)
        .contains(
            "AuthenticatedSessionCookieWriter",
            "ValidateAuthenticatedSessionUseCase",
            "sessionCookies.cookieName()",
            "RequestHeaderContextKeys.XSRF_TOKEN_HEADER",
            "resolvePortalAccessContext(accountRef,sessionId)")
        .doesNotContain("AUTHENTICATED_SESSION_HEADER", "X-Authenticated-Session-Id");
  }

  @Test
  void temporaryAdapterIsNotInjectedDirectly() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"));
    assertThat(s)
        .contains("PortalAccessContextPort")
        .doesNotContain("TemporaryPortalAccessContextAdapter", "Redis", "AuthenticatedSessionPort");
  }
}
