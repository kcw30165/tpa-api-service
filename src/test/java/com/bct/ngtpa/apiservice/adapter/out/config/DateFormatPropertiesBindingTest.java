package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DateFormatPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        display-format:
                          date:
                            en:
                              default: dd/MM/yyyy
                              JP: MM/dd/yyyy
                            zh_HK:
                              default: yyyy-MM-dd
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                    var propertySources = new YamlPropertySourceLoader().load("dateFormatTest", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void bindsEnLocaleFormats() {
        contextRunner.run(context -> {
            var bound = context.getBean(DateFormatProperties.class);
            var enFormats = bound.getLocaleFormats("en");
            assertNotNull(enFormats);
            assertEquals("dd/MM/yyyy", enFormats.get("default"));
            assertEquals("MM/dd/yyyy", enFormats.get("JP"));
        });
    }

    @Test
    void bindsZhHkLocaleFormats() {
        contextRunner.run(context -> {
            var bound = context.getBean(DateFormatProperties.class);
            assertEquals("yyyy-MM-dd", bound.getLocaleFormats("zh_HK").get("default"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DateFormatProperties.class)
    static class TestConfig {
    }
}
