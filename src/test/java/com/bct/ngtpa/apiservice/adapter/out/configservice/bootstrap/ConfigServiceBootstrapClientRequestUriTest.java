package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage for {@link ConfigServiceBootstrapClient#request(ConfigServiceBootstrapSettings)}
 * and its private {@code trimTrailingSlashes} helper.
 *
 * <p>{@code ConfigServiceLoaderStartupSmokeTest} is fail-soft by design (D1) and only asserts
 * {@code context.isActive()}, so it cannot prove these specific lines execute. This test exercises
 * them directly: a reflection-based unit test for {@code trimTrailingSlashes}, and a real local
 * HTTP server (JDK {@link HttpServer}, no new dependency) proving {@code fetch()} builds the exact
 * same request path whether the configured base URL has zero, one, or several trailing slashes.
 */
class ConfigServiceBootstrapClientRequestUriTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void trimTrailingSlashesRemovesAnyNumberOfTrailingSlashesAndLeavesOtherwiseUnchanged()
      throws Exception {
    Method method =
        ConfigServiceBootstrapClient.class.getDeclaredMethod("trimTrailingSlashes", String.class);
    method.setAccessible(true);
    assertThat((String) method.invoke(null, "http://host:8080")).isEqualTo("http://host:8080");
    assertThat((String) method.invoke(null, "http://host:8080/")).isEqualTo("http://host:8080");
    assertThat((String) method.invoke(null, "http://host:8080///")).isEqualTo("http://host:8080");
    assertThat((String) method.invoke(null, "")).isEqualTo("");
    assertThat((String) method.invoke(null, "/")).isEqualTo("");
  }

  @Test
  void fetchBuildsTheSameRequestPathRegardlessOfTrailingSlashesInBaseUrl() throws Exception {
    AtomicReference<String> capturedPath = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/configs",
        exchange -> {
          capturedPath.set(
              exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getQuery());
          byte[] body = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    int port = server.getAddress().getPort();

    ConfigServiceBootstrapClient client = new ConfigServiceBootstrapClient();

    for (String baseUrl :
        List.of(
            "http://127.0.0.1:" + port,
            "http://127.0.0.1:" + port + "/",
            "http://127.0.0.1:" + port + "///")) {
      capturedPath.set(null);
      ConfigServiceBootstrapSettings settings = settingsWithBaseUrl(baseUrl);
      List<ConfigServiceBootstrapEntry> entries = client.fetch(settings);
      assertThat(entries).isEmpty();
      assertThat(capturedPath.get())
          .as("captured path for baseUrl=%s", baseUrl)
          .isEqualTo("/api/configs?application=app&profile=profile&label=label");
    }
  }

  /**
   * Builds a real {@code ConfigServiceBootstrapSettings} record via reflection on its record
   * components, so this test does not depend on guessing the component declaration order.
   */
  private static ConfigServiceBootstrapSettings settingsWithBaseUrl(String baseUrl)
      throws Exception {
    RecordComponent[] components = ConfigServiceBootstrapSettings.class.getRecordComponents();
    Class<?>[] types = new Class<?>[components.length];
    Object[] args = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      String name = components[i].getName();
      Class<?> type = components[i].getType();
      types[i] = type;
      args[i] = valueFor(name, type, baseUrl);
    }
    Constructor<ConfigServiceBootstrapSettings> constructor =
        ConfigServiceBootstrapSettings.class.getDeclaredConstructor(types);
    constructor.setAccessible(true);
    return constructor.newInstance(args);
  }

  private static Object valueFor(String name, Class<?> type, String baseUrl) {
    switch (name) {
      case "baseUrl":
        return baseUrl;
      case "application":
        return "app";
      case "profile":
        return "profile";
      case "label":
        return "label";
      case "connectTimeoutMs":
      case "readTimeoutMs":
        return 2000;
      case "maxAttempts":
        return 1;
      case "backoffMs":
        return 0;
      case "username":
      case "password":
        return type == String.class ? "" : null;
      default:
        if (type == int.class) {
          return 0;
        }
        if (type == long.class) {
          return 0L;
        }
        if (type == boolean.class) {
          return false;
        }
        return null;
    }
  }
}
