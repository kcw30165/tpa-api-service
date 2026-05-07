package com.bct.ngtpa.apiservice.config.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

class LoggingSanitizerPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        logging-sanitizer:
                          sensitive-tokens:
                            - authorization
                            - apiKey
                            - policy-no
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                    var propertySources = new YamlPropertySourceLoader().load("loggingSanitizerTest", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load logging sanitizer YAML", ex);
                }
            });

    @Test
    void bindsSensitiveTokensFromYaml() {
        contextRunner.run(context -> {
            var bound = context.getBean(LoggingSanitizerProperties.class);
            assertEquals(List.of("authorization", "apiKey", "policy-no"), bound.getSensitiveTokens());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoggingSanitizerProperties.class)
    static class TestConfig {
    }
}