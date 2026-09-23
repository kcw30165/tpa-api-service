package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Fail-first: ConfigServiceEnvironmentPostProcessor does not exist yet.
 *
 * <p>Encodes the plan's fail-soft policy (D1) and two-tier precedence (D2). The loader must never
 * prevent startup, and a Config Service value must beat an application.yml default.
 */
class ConfigServiceEnvironmentPostProcessorTest {

  private static final String YAML_SOURCE = "applicationYamlStub";
  private static final String KEY = "fund-price.export.headers.fund-name";

  private MockEnvironment environmentWithYamlDefault() {
    var environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addLast(new MapPropertySource(YAML_SOURCE, java.util.Map.of(KEY, "yaml-default")));
    environment.setProperty("config-service.base-url", "http://config-service");
    return environment;
  }

  private ConfigServiceEnvironmentPostProcessor processorReturning(
      List<ConfigServiceBootstrapEntry> entries) {
    return new ConfigServiceEnvironmentPostProcessor(settings -> entries);
  }

  private ConfigServiceEnvironmentPostProcessor processorFailingWith(RuntimeException failure) {
    return new ConfigServiceEnvironmentPostProcessor(
        settings -> {
          throw failure;
        });
  }

  @Test
  void remoteValueOverridesTheYamlDefault() {
    var environment = environmentWithYamlDefault();

    processorReturning(List.of(new ConfigServiceBootstrapEntry(KEY, "remote-value")))
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(KEY)).isEqualTo("remote-value");
  }

  @Test
  void blankBaseUrlSkipsTheFetchEntirely() {
    var environment = new MockEnvironment();
    var calls = new AtomicInteger();
    var processor =
        new ConfigServiceEnvironmentPostProcessor(
            settings -> {
              calls.incrementAndGet();
              return List.of();
            });

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(calls).hasValue(0);
    assertThat(
            environment
                .getPropertySources()
                .contains(ConfigServicePropertySourceFactory.SOURCE_NAME))
        .isFalse();
  }

  @Test
  void transportFailureLeavesTheYamlDefaultIntactAndDoesNotThrow() {
    var environment = environmentWithYamlDefault();

    processorFailingWith(new IllegalStateException("connection refused"))
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(KEY)).isEqualTo("yaml-default");
  }

  @Test
  void malformedResponseFailureDoesNotPreventStartup() {
    var environment = environmentWithYamlDefault();

    processorFailingWith(new IllegalArgumentException("malformed json"))
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(KEY)).isEqualTo("yaml-default");
  }

  @Test
  void emptyRemoteResultLeavesTheYamlDefaultIntact() {
    var environment = environmentWithYamlDefault();

    processorReturning(List.of()).postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(KEY)).isEqualTo("yaml-default");
  }

  @Test
  void remoteSourceIsRegisteredAheadOfTheYamlSource() {
    var environment = environmentWithYamlDefault();

    processorReturning(List.of(new ConfigServiceBootstrapEntry(KEY, "remote-value")))
        .postProcessEnvironment(environment, new SpringApplication());

    var names =
        environment.getPropertySources().stream()
            .map(org.springframework.core.env.PropertySource::getName)
            .toList();

    assertThat(names.indexOf(ConfigServicePropertySourceFactory.SOURCE_NAME))
        .isLessThan(names.indexOf(YAML_SOURCE));
  }

  @Test
  void rerunningTheProcessorDoesNotDuplicateTheSource() {
    var environment = environmentWithYamlDefault();
    var processor = processorReturning(List.of(new ConfigServiceBootstrapEntry(KEY, "remote")));

    processor.postProcessEnvironment(environment, new SpringApplication());
    processor.postProcessEnvironment(environment, new SpringApplication());

    long count =
        environment.getPropertySources().stream()
            .filter(s -> ConfigServicePropertySourceFactory.SOURCE_NAME.equals(s.getName()))
            .count();

    assertThat(count).isEqualTo(1L);
  }
}
