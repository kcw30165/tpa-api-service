package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

/**
 * Confirms masking.logging.sensitive-tokens binds into {@link MaskingProperties} correctly.
 *
 * <p>Formerly bound logging-sanitizer.sensitive-tokens into the now-deleted
 * LoggingSanitizerProperties; relocated as part of the centralized masking policy migration
 * (Phase 3b) so all masking-related configuration lives under one masking.* tree.
 */
class LoggingSanitizerPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withInitializer(
              context -> {
                var yaml =
                    """
                        masking:
                          logging:
                            sensitive-tokens:
                              - authorization
                              - apiKey
                              - policy-no
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                  var propertySources =
                      new YamlPropertySourceLoader().load("maskingLoggingTest", resource);
                  propertySources.forEach(
                      source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                  throw new IllegalStateException("Failed to load masking logging YAML", ex);
                }
              });

  @Test
  void bindsSensitiveTokensFromYaml() {
    contextRunner.run(
        context -> {
          var bound = context.getBean(MaskingProperties.class);
          assertEquals(
              List.of("authorization", "apiKey", "policy-no"),
              bound.getLogging().getSensitiveTokens());
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(MaskingProperties.class)
  static class TestConfig {}
}
