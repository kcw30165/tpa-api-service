package com.bct.ngtpa.apiservice.adapter.out.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceDatePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        reference-date:
                          account-env: sit
                          override-date: 31/03/2026
                          override-zone-id: Asia/Hong_Kong
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                    var propertySources = new YamlPropertySourceLoader().load("referenceDateTest", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void bindsAccountEnvFromYaml() {
        contextRunner.run(context -> {
            var props = context.getBean(ReferenceDateProperties.class);
            assertEquals("sit", props.getAccountEnv());
        });
    }

    @Test
    void bindsOverrideDateAndZoneId() {
        contextRunner.run(context -> {
            var props = context.getBean(ReferenceDateProperties.class);
            assertEquals("31/03/2026", props.getOverrideDate());
            assertEquals("Asia/Hong_Kong", props.getOverrideZoneId());
        });
    }

    @Test
    void accountEnvDefaultsToEmptyString() {
        var emptyRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class);

        emptyRunner.run(context -> {
            var props = context.getBean(ReferenceDateProperties.class);
            assertEquals("", props.getAccountEnv());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReferenceDateProperties.class)
    static class TestConfig {
    }
}
