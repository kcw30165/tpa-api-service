package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Proves D1 end to end: the application boots successfully even when Config Service is
 * completely unreachable, and does so within the plan's bootstrap timeout budget.
 *
 * <p>Unlike the unit tests in {@link ConfigServiceEnvironmentPostProcessorTest}, which
 * exercise the fail-soft branches against a stubbed fetcher, this test runs the REAL
 * {@link ConfigServiceBootstrapClient} against a genuinely unreachable address, proving
 * the whole chain -- HTTP client, retry/backoff, and environment registration -- degrades
 * to configured defaults rather than blocking startup.
 *
 * <p><strong>Isolation note (corrected in 016):</strong> {@code spring.config.name} is set
 * to a name that does not resolve to any real config file. Spring Boot otherwise loads
 * {@code application.yml} from the classpath regardless of which
 * {@code @SpringBootApplication} class is used, and that file defines the
 * {@code redis-mtls} SSL bundle pointing at a certificate that does not exist in this
 * environment -- an unrelated infrastructure dependency that has nothing to do with the
 * Config Service loader under test here. Without this override the context fails during
 * Micrometer SSL metrics binding before the environment-post-processor phase is even
 * reached.
 */
class ConfigServiceLoaderStartupSmokeTest {

  @SpringBootApplication
  static class MinimalBootApplication {}

  /** An address nothing listens on, so every connection attempt fails fast. */
  private String unreachableBaseUrl() throws IOException {
    int freePort;
    try (ServerSocket socket = new ServerSocket(0)) {
      freePort = socket.getLocalPort();
    }
    // The socket above is now closed, so this port refuses connections immediately.
    return "http://127.0.0.1:" + freePort;
  }

  private SpringApplicationBuilder isolatedBuilder() {
    return new SpringApplicationBuilder(MinimalBootApplication.class)
        .web(WebApplicationType.NONE)
        .properties(
            // Prevents application.yml (and its redis-mtls SSL bundle) from being
            // picked up at all. This is the fix for the 015 failure.
            "spring.config.name=config-service-loader-smoke-test-isolated",
            "spring.main.banner-mode=off");
  }

  @Test
  void applicationStartsWhenConfigServiceIsUnreachable() throws Exception {
    String baseUrl = unreachableBaseUrl();

    ConfigurableApplicationContext context = null;
    try {
      long start = System.nanoTime();

      context =
          isolatedBuilder()
              .properties(
                  "config-service.base-url=" + baseUrl,
                  "config-service.bootstrap.connect-timeout-ms=200",
                  "config-service.bootstrap.read-timeout-ms=200",
                  "config-service.bootstrap.max-attempts=2",
                  "config-service.bootstrap.backoff-ms=50")
              .run();

      long elapsed = System.nanoTime() - start;

      assertThat(context.isActive())
          .as("application must start even though Config Service is unreachable")
          .isTrue();

      // Generous ceiling: 2 attempts * (200ms timeout + 50ms backoff) plus JVM/context
      // overhead. Proves the loader does not silently hang past its configured budget.
      assertThat(Duration.ofNanos(elapsed))
          .as("bootstrap must not hang past its configured retry/backoff budget")
          .isLessThan(Duration.ofSeconds(15));
    } finally {
      if (context != null) {
        context.close();
      }
    }
  }

  @Test
  void applicationStartsWhenConfigServiceBaseUrlIsAbsent() {
    ConfigurableApplicationContext context = isolatedBuilder().run();
    try {
      assertThat(context.isActive()).isTrue();
    } finally {
      context.close();
    }
  }
}
