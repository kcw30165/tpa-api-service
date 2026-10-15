package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Minimal JDK-HttpClient Config Service reader used during bootstrap.
 *
 * <p>This duplicates a small part of {@code ConfigServiceWebClientAdapter} on purpose: that adapter
 * is a Spring bean and no application context exists yet. Both target the same {@code GET
 * /api/configs} contract; keep them in step.
 *
 * <p>Throws on any failure. Fail-soft handling belongs to {@link
 * ConfigServiceEnvironmentPostProcessor}, which is the single place that decides the application
 * may continue on YAML defaults.
 */
public final class ConfigServiceBootstrapClient implements ConfigServiceBootstrapFetcher {

  private static final String CONFIGS_PATH = "/api/configs";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public List<ConfigServiceBootstrapEntry> fetch(ConfigServiceBootstrapSettings settings) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(settings.connectTimeoutMs()))
            .build();

    HttpRequest request = request(settings);
    RuntimeException lastFailure = null;

    for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
      try {
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          // Status only. The body is never surfaced: it may contain configuration values.
          throw new IllegalStateException(
              "Config Service returned status " + response.statusCode());
        }
        return parse(response.body());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Config Service bootstrap fetch interrupted");
      } catch (RuntimeException exception) {
        lastFailure = exception;
      } catch (Exception exception) {
        lastFailure = new IllegalStateException("Config Service bootstrap fetch failed");
      }

      if (attempt < settings.maxAttempts()) {
        sleep(settings.backoffMs());
      }
    }
    throw lastFailure == null
        ? new IllegalStateException("Config Service bootstrap fetch failed")
        : lastFailure;
  }

  private HttpRequest request(ConfigServiceBootstrapSettings settings) {
    String uri =
        trimTrailingSlashes(settings.baseUrl())
            + CONFIGS_PATH
            + "?application="
            + encode(settings.application())
            + "&profile="
            + encode(settings.profile())
            + "&label="
            + encode(settings.label());

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(uri))
            .GET()
            .timeout(Duration.ofMillis(settings.readTimeoutMs()))
            .header("Accept", "application/json");

    if (settings.hasCredentials()) {
      String token =
          Base64.getEncoder()
              .encodeToString(
                  (settings.username() + ":" + settings.password())
                      .getBytes(StandardCharsets.UTF_8));
      builder.header("Authorization", "Basic " + token);
    }
    return builder.build();
  }

  private List<ConfigServiceBootstrapEntry> parse(String body) {
    try {
      CollectionType type =
          objectMapper
              .getTypeFactory()
              .constructCollectionType(List.class, ConfigServiceBootstrapEntry.class);
      List<ConfigServiceBootstrapEntry> entries = objectMapper.readValue(body, type);
      return entries == null ? List.of() : entries;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Config Service response could not be parsed");
    }
  }

  private static String trimTrailingSlashes(String value) {
    int end = value.length();
    while (end > 0 && value.charAt(end - 1) == '/') {
      end--;
    }
    return value.substring(0, end);
  }

  private static String encode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static void sleep(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
