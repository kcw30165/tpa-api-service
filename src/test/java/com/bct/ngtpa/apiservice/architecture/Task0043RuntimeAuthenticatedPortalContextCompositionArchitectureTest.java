package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043RuntimeAuthenticatedPortalContextCompositionArchitectureTest {
  @Test
  void sessionConfigurationOwnsPrimaryAuthenticatedPortalContextPort() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java"));
    assertThat(source)
        .contains(
            "@Primary",
            "PortalAccessContextPort authenticatedPortalAccessContextPort",
            "new AuthenticatedAccountContextResolver(lifecycle)");
  }

  @Test
  void filterRetainsProjectLombokConstructorStyleAndPortBoundary() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"));
    assertThat(source)
        .contains(
            "@RequiredArgsConstructor",
            "private final PortalAccessContextPort portalAccessContextPort");
    assertThat(source)
        .doesNotContain(
            "public PortalAccessContextWebFilter(",
            "TemporaryPortalAccessContextAdapter",
            "AuthenticatedSessionLifecycleService");
  }
}
