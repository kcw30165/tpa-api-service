package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Fail-first: ConfigServiceBootstrapSettings does not exist yet.
 *
 * <p>Pins the D3/D4/D5 defaults and the bootstrap timeout block (D7) from the implementation plan.
 */
class ConfigServiceBootstrapSettingsTest {

  @Test
  void appliesPlannedDefaultsWhenNothingIsConfigured() {
    var environment = new MockEnvironment();

    var settings = ConfigServiceBootstrapSettings.from(environment);

    assertThat(settings.application()).isEqualTo("ngtpa-api-service");
    assertThat(settings.label()).isEqualTo("main");
    assertThat(settings.baseUrl()).isEmpty();
    assertThat(settings.connectTimeoutMs()).isEqualTo(2000);
    assertThat(settings.readTimeoutMs()).isEqualTo(3000);
    assertThat(settings.maxAttempts()).isEqualTo(2);
    assertThat(settings.backoffMs()).isEqualTo(500);
  }

  @Test
  void loaderIsDisabledWhenBaseUrlIsBlank() {
    var environment = new MockEnvironment().withProperty("config-service.base-url", "   ");

    assertThat(ConfigServiceBootstrapSettings.from(environment).enabled()).isFalse();
  }

  @Test
  void loaderIsEnabledWhenBaseUrlIsPresent() {
    var environment =
        new MockEnvironment().withProperty("config-service.base-url", "http://config-service");

    assertThat(ConfigServiceBootstrapSettings.from(environment).enabled()).isTrue();
  }

  @Test
  void profileDefaultsToLowercasedFirstActiveProfile() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("SIT", "shared");

    assertThat(ConfigServiceBootstrapSettings.from(environment).profile()).isEqualTo("sit");
  }

  @Test
  void explicitProfilePropertyOverridesTheActiveProfile() {
    var environment = new MockEnvironment().withProperty("config-service.profile", "uat");
    environment.setActiveProfiles("PROD");

    assertThat(ConfigServiceBootstrapSettings.from(environment).profile()).isEqualTo("uat");
  }

  @Test
  void productionAndDisasterRecoveryBothMapToMainOnlyWhenConfigured() {
    // D5: DR uses `main`, but that is a deployment decision expressed through the
    // dedicated property -- the loader must NOT silently map PROD/DR itself.
    var environment = new MockEnvironment();
    environment.setActiveProfiles("DR");

    assertThat(ConfigServiceBootstrapSettings.from(environment).profile()).isEqualTo("dr");

    var configured = new MockEnvironment().withProperty("config-service.profile", "main");
    configured.setActiveProfiles("DR");

    assertThat(ConfigServiceBootstrapSettings.from(configured).profile()).isEqualTo("main");
  }

  @Test
  void profileIsEmptyWhenNoActiveProfileAndNoOverride() {
    assertThat(ConfigServiceBootstrapSettings.from(new MockEnvironment()).profile()).isEmpty();
  }

  @Test
  void overridesEveryBootstrapTimeoutValue() {
    var environment =
        new MockEnvironment()
            .withProperty("config-service.base-url", "http://config-service")
            .withProperty("config-service.bootstrap.connect-timeout-ms", "750")
            .withProperty("config-service.bootstrap.read-timeout-ms", "900")
            .withProperty("config-service.bootstrap.max-attempts", "5")
            .withProperty("config-service.bootstrap.backoff-ms", "125");

    var settings = ConfigServiceBootstrapSettings.from(environment);

    assertThat(settings.connectTimeoutMs()).isEqualTo(750);
    assertThat(settings.readTimeoutMs()).isEqualTo(900);
    assertThat(settings.maxAttempts()).isEqualTo(5);
    assertThat(settings.backoffMs()).isEqualTo(125);
  }

  @Test
  void toStringNeverRevealsCredentials() {
    var settings =
        ConfigServiceBootstrapSettings.from(
            new MockEnvironment()
                .withProperty("config-service.base-url", "http://config-service")
                .withProperty("config-service.username", "svc-user")
                .withProperty("config-service.password", "svc-secret"));

    assertThat(settings.toString()).doesNotContain("svc-user", "svc-secret");
  }
}
