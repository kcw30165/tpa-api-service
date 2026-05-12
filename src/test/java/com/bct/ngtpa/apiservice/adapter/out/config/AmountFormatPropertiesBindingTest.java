package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountFormatPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        display-format:
                          amount:
                            en:
                              default:
                                min-fraction-digits: 0
                                max-fraction-digits: 2
                                grouping-separator: ","
                                decimal-separator: "."
                                rounding-mode: HALF_UP
                                strip-trailing-zeros: true
                                negative-style: minus
                              JP:
                                min-fraction-digits: 0
                                max-fraction-digits: 0
                                grouping-separator: ","
                                decimal-separator: "."
                                rounding-mode: HALF_UP
                                strip-trailing-zeros: true
                                negative-style: minus
                            zh_HK:
                              default:
                                min-fraction-digits: 0
                                max-fraction-digits: 2
                                grouping-separator: ","
                                decimal-separator: "."
                                rounding-mode: HALF_UP
                                strip-trailing-zeros: true
                                negative-style: minus
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
    void bindsEnLocaleDefaultConfig() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var enDefault = bound.getLocaleFormats("en").get("default");
            assertNotNull(enDefault);
            assertEquals(0, enDefault.getMinFractionDigits());
            assertEquals(2, enDefault.getMaxFractionDigits());
            assertEquals(",", enDefault.getGroupingSeparator());
            assertEquals(".", enDefault.getDecimalSeparator());
            assertEquals(RoundingMode.HALF_UP, enDefault.getRoundingMode());
            assertTrue(enDefault.isStripTrailingZeros());
            assertEquals("minus", enDefault.getNegativeStyle());
        });
    }

    @Test
    void bindsEnLocaleJpEnvConfig() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var jpConfig = bound.getLocaleFormats("en").get("JP");
            assertNotNull(jpConfig);
            assertEquals(0, jpConfig.getMaxFractionDigits());
        });
    }

    @Test
    void bindsZhHkLocaleConfig() {
        contextRunner.run(context -> {
            var bound = context.getBean(AmountFormatProperties.class);
            var zhDefault = bound.getLocaleFormats("zh_HK").get("default");
            assertNotNull(zhDefault);
            assertEquals(2, zhDefault.getMaxFractionDigits());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AmountFormatProperties.class)
    static class TestConfig {
    }
}
