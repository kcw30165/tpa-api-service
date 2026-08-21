package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CsrfTokenRefreshWebArchitectureGuardTest {
  @Test
  void refreshRouteIsExplicitlyPermittedBeforeAuthenticatedExchangeRule() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/infrastructure/security/SecurityConfig.java"));
    int refresh = source.indexOf("/api/v1/auth/csrf-token/refresh");
    int catchAll = source.indexOf(".anyExchange()");
    assertThat(refresh).isGreaterThanOrEqualTo(0).isLessThan(catchAll);
    assertThat(source.substring(refresh, catchAll)).contains(".permitAll()");
  }

  @Test
  void applicationCsrfRefreshPackageHasNoWebOrRedisDependencies() throws Exception {
    Path packagePath =
        Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/csrf");
    try (var files = Files.list(packagePath)) {
      for (Path file : files.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        assertThat(source)
            .doesNotContain("org.springframework.web", "adapter.out.redis", "ReactiveRedis");
      }
    }
  }

  @Test
  void controllerUsesConfiguredCookieNamesAndWritesNoCookies() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/csrf/CsrfTokenRefreshController.java"));
    assertThat(source).doesNotContain("portal_sid", "auth_flow_sid");
    assertThat(source).doesNotContain("addCookie", "XSRF_TOKEN_HEADER");
    assertThat(source)
        .contains("getCookie().getName()", "CacheControl.noStore()", "HttpHeaders.PRAGMA");
  }
}
