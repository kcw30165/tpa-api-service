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

class AmountFormatPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        amount-display-format:
                          en:
                            amount: "#,##0.00"
                            amount.JP: "#,##0.000"
                          zh_HK:
                            amount: "#,##0.00"
                            amount.JP: "#,##0.000"
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                    var propertySources = new YamlPropertySourceLoader().load("amountFormatTest", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void bindsEnLocaleBaseAmountPattern() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var pattern = bound.getLocaleFormats("en").get("amount");
            assertNotNull(pattern);
            assertEquals("#,##0.00", pattern);
        });
    }

    @Test
    void bindsEnLocaleJpAmountPattern() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var pattern = bound.getLocaleFormats("en").get("amount.JP");
            assertNotNull(pattern);
            assertEquals("#,##0.000", pattern);
        });
    }

    @Test
    void bindsZhHkLocaleBaseAmountPattern() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var pattern = bound.getLocaleFormats("zh_HK").get("amount");
            assertNotNull(pattern);
            assertEquals("#,##0.00", pattern);
        });
    }

    @Test
    void bindsZhHkLocaleJpAmountPattern() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var pattern = bound.getLocaleFormats("zh_HK").get("amount.JP");
            assertNotNull(pattern);
            assertEquals("#,##0.000", pattern);
        });
    }

    @Test
    void returnsEmptyMapForUnknownLocale() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var formats = bound.getLocaleFormats("fr");
            assertNotNull(formats);
            assertEquals(0, formats.size());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AmountFormatProperties.class)
    static class TestConfig {
    }
}
