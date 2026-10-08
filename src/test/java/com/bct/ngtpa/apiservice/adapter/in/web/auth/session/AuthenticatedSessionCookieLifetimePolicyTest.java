package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/**
 * Pins the authenticated session cookie lifetime contract: the shipped default tracks the absolute
 * session timeout, and a cookie that would outlive it fails fast at startup.
 */
class AuthenticatedSessionCookieLifetimePolicyTest {
  private static final String COOKIE_ENV = "AUTH_WORKFLOWS_LOGIN_SESSION_COOKIE_MAX_AGE_SECONDS";
  private static final String ABSOLUTE_ENV =
      "AUTH_WORKFLOWS_LOGIN_SESSION_ABSOLUTE_TIMEOUT_SECONDS";
  private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
  private static final Path WEB_CONFIG =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/session/AuthenticatedSessionWebConfig.java");

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private Logger policyLogger;

  @BeforeEach
  void attachAppender() {
    policyLogger = (Logger) LoggerFactory.getLogger(AuthenticatedSessionCookieLifetimePolicy.class);
    logs.start();
    policyLogger.addAppender(logs);
  }

  @AfterEach
  void detachAppender() {
    policyLogger.detachAppender(logs);
    logs.stop();
  }

  @Test
  void cookieEqualToAbsoluteTimeoutIsAcceptedSilently() {
    assertThatCode(
            () ->
                AuthenticatedSessionCookieLifetimePolicy.requireWithinAbsoluteTimeout(28800, 28800))
        .doesNotThrowAnyException();
    assertThat(logs.list).isEmpty();
  }

  @Test
  void cookieShorterThanAbsoluteTimeoutIsAcceptedButWarns() {
    AuthenticatedSessionCookieLifetimePolicy.requireWithinAbsoluteTimeout(900, 28800);

    assertThat(logs.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel().toString()).isEqualTo("WARN");
              assertThat(event.getFormattedMessage())
                  .contains(
                      "event=authenticated.session.cookie.lifetime.shorter-than-absolute",
                      "cookieMaxAgeSeconds=900",
                      "absoluteTimeoutSeconds=28800");
            });
  }

  @Test
  void cookieLongerThanAbsoluteTimeoutFailsFast() {
    assertThatThrownBy(
            () ->
                AuthenticatedSessionCookieLifetimePolicy.requireWithinAbsoluteTimeout(28801, 28800))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth-workflows.login.session.cookie.max-age-seconds (28801)")
        .hasMessageContaining("auth-workflows.login.session.absolute-timeout-seconds (28800)");
  }

  @Test
  void nonPositiveAbsoluteTimeoutIsLeftToSessionLifecycleConfig() {
    assertThatCode(
            () -> AuthenticatedSessionCookieLifetimePolicy.requireWithinAbsoluteTimeout(900, 0))
        .doesNotThrowAnyException();
    assertThat(logs.list).isEmpty();
  }

  @Test
  void shippedYamlDefaultCookieLifetimeTracksTheAbsoluteTimeout() {
    String raw = cookieMaxAgeRawValue();

    assertThat(resolve(raw, Map.of())).isEqualTo("28800");
    assertThat(resolve(raw, Map.of(ABSOLUTE_ENV, "600"))).isEqualTo("600");
    assertThat(resolve(raw, Map.of(ABSOLUTE_ENV, "600", COOKIE_ENV, "300"))).isEqualTo("300");
  }

  @Test
  void webConfigAppliesThePolicyBeforeBuildingTheCookieWriter() throws IOException {
    String source = Files.readString(WEB_CONFIG);

    assertThat(source)
        .contains("AuthenticatedSessionCookieLifetimePolicy.requireWithinAbsoluteTimeout(");
    assertThat(source.indexOf("requireWithinAbsoluteTimeout("))
        .isLessThan(source.indexOf("new AuthenticatedSessionCookieWriter("));
  }

  private static String cookieMaxAgeRawValue() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new FileSystemResource(APPLICATION_YML));
    Properties properties = yaml.getObject();
    assertThat(properties).as("application.yml must be readable").isNotNull();
    List<String> matches =
        properties.stringPropertyNames().stream()
            .filter(key -> key.endsWith(".cookie.max-age-seconds"))
            .filter(key -> properties.getProperty(key).contains(COOKIE_ENV))
            .toList();
    assertThat(matches)
        .as("exactly one session cookie max-age-seconds entry bound to %s", COOKIE_ENV)
        .hasSize(1);
    return properties.getProperty(matches.get(0));
  }

  private static String resolve(String raw, Map<String, Object> overrides) {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
    return environment.resolveRequiredPlaceholders(raw);
  }
}
