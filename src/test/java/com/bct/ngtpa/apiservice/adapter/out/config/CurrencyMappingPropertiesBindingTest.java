package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.adapter.out.config.CurrencyMappingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyMappingPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        currencyMapping:
                          en:
                            HKD: HKD
                            EUR.TB.HKBU: EUR
                          zh_HK:
                            HKD: 港元
                            HKD.OG: 港幣
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                    var propertySources = new YamlPropertySourceLoader().load("currencyMappingTest", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load currency mapping YAML", ex);
                }
            });

    @Test
    void bindsLocalesAndDottedCurrencyKeys() {
        contextRunner.run(context -> {
            var bound = context.getBean(CurrencyMappingProperties.class);

            assertEquals("HKD", bound.getLocaleMappings("en").get("HKD"));
            assertEquals("港元", bound.getLocaleMappings("zh_HK").get("HKD"));
            assertEquals("EUR", bound.getLocaleMappings("en").get("EUR.TB.HKBU"));
            assertEquals("港幣", bound.getLocaleMappings("zh_HK").get("HKD.OG"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CurrencyMappingProperties.class)
    static class TestConfig {
    }
}
