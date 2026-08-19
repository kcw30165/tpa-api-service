package com.bct.ngtpa.apiservice.adapter.in.web.session.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class AuthFlowSessionSessionIdConfigBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withInitializer(
              context -> {
                var resource = new ClassPathResource("application.yml");
                try {
                  var propertySources =
                      new YamlPropertySourceLoader().load("applicationConfig", resource);
                  propertySources.forEach(
                      source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                  throw new IllegalStateException("Failed to load YAML", ex);
                }
              });

  @Test
  void bindsAuthFlowSessionIdPolicy() {
    contextRunner.run(
        context -> {
          var properties = context.getBean(AuthFlowSessionProperties.class);
          assertThat(properties.getSessionId().getPrefix()).isEqualTo("afs");
          assertThat(properties.getSessionId().getEntropyBytes()).isEqualTo(16);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AuthFlowSessionProperties.class)
  static class TestConfig {}
}
