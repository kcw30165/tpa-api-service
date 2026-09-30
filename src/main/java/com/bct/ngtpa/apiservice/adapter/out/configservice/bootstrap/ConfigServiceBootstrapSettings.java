package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import java.util.Locale;
import org.springframework.core.env.Environment;

/**
 * Connection and partition settings read directly from the raw {@link Environment}.
 *
 * <p>This intentionally does NOT reuse {@code ConfigServiceProperties}: a
 * {@code @ConfigurationProperties} bean does not exist when an {@code EnvironmentPostProcessor}
 * runs. Do not "fix" this by injecting the existing bean.
 *
 * <p>Defaults follow decisions D3, D4, D5 and D7 of the implementation plan.
 */
public record ConfigServiceBootstrapSettings(
    String baseUrl,
    String username,
    String password,
    String application,
    String profile,
    String label,
    int connectTimeoutMs,
    int readTimeoutMs,
    int maxAttempts,
    int backoffMs) {

  private static final String DEFAULT_APPLICATION = "ngtpa-api-service";
  private static final String DEFAULT_LABEL = "main";

  public static ConfigServiceBootstrapSettings from(Environment environment) {
    return new ConfigServiceBootstrapSettings(
        text(environment, "config-service.base-url", ""),
        text(environment, "config-service.username", ""),
        text(environment, "config-service.password", ""),
        text(environment, "config-service.application", DEFAULT_APPLICATION),
        resolveProfile(environment),
        text(environment, "config-service.label", DEFAULT_LABEL),
        number(environment, "config-service.bootstrap.connect-timeout-ms", 2000),
        number(environment, "config-service.bootstrap.read-timeout-ms", 3000),
        number(environment, "config-service.bootstrap.max-attempts", 2),
        number(environment, "config-service.bootstrap.backoff-ms", 500));
  }

  /** The loader is disabled entirely when no base URL is configured. */
  public boolean enabled() {
    return !baseUrl.isBlank();
  }

  public boolean hasCredentials() {
    return !username.isBlank();
  }

  /**
   * D5: an explicit {@code config-service.profile} wins; otherwise the first active Spring profile,
   * lowercased. PROD/DR are NOT silently mapped to {@code main} here -- that is a deployment
   * decision expressed through the property.
   */
  private static String resolveProfile(Environment environment) {
    String explicit = text(environment, "config-service.profile", "");
    if (!explicit.isBlank()) {
      return explicit.toLowerCase(Locale.ROOT);
    }
    String[] active = environment.getActiveProfiles();
    return active.length == 0 ? "" : active[0].trim().toLowerCase(Locale.ROOT);
  }

  private static String text(Environment environment, String key, String fallback) {
    String value = environment.getProperty(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static int number(Environment environment, String key, int fallback) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  @Override
  public String toString() {
    return "ConfigServiceBootstrapSettings[baseUrl="
        + baseUrl
        + ", application="
        + application
        + ", profile="
        + profile
        + ", label="
        + label
        + ", credentials="
        + (hasCredentials() ? "<present>" : "<absent>")
        + ", connectTimeoutMs="
        + connectTimeoutMs
        + ", readTimeoutMs="
        + readTimeoutMs
        + ", maxAttempts="
        + maxAttempts
        + ", backoffMs="
        + backoffMs
        + "]";
  }
}
