package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StructuredConsoleLoggingConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(EmptyConfiguration.class);

  @Test
  void nonLocalProfileUsesBootNativeEcsConsoleLogging() {
    contextRunner
        .withPropertyValues("spring.profiles.active=sit")
        .run(
            context ->
                assertThat(
                        context.getEnvironment().getProperty("logging.structured.format.console"))
                    .isEqualTo("ecs"));
  }

  @Test
  void localProfileKeepsReadableConsoleLogging() {
    contextRunner
        .withPropertyValues("spring.profiles.active=local")
        .run(
            context ->
                assertThat(
                        context.getEnvironment().getProperty("logging.structured.format.console"))
                    .isNull());
  }

  @Configuration(proxyBeanMethods = false)
  static class EmptyConfiguration {}
}
