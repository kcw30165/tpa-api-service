package com.bct.ngtpa.apiservice.adapter.out.configservice;

import com.bct.ngtpa.apiservice.adapter.out.configservice.config.ConfigServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigServicePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var yaml = """
                        config-service:
                          base-url: http://localhost:8080
                          timeout-milliseconds: 5000
                        """;
                var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
                try {
                    var propertySources = new YamlPropertySourceLoader().load("configServiceTest", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void bindsBaseUrlAndTimeout() {
        contextRunner.run(context -> {
            var props = context.getBean(ConfigServiceProperties.class);
            assertEquals("http://localhost:8080", props.getBaseUrl());
            assertEquals(5000, props.getTimeoutMilliseconds());
        });
    }

    @Test
    void defaultTimeoutIs10000() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("config-service.base-url=http://localhost:9090")
                .run(context -> {
                    var props = context.getBean(ConfigServiceProperties.class);
                    assertEquals(10000, props.getTimeoutMilliseconds());
                });
    }

    @Test
    void defaultBaseUrlIsEmpty() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    var props = context.getBean(ConfigServiceProperties.class);
                    assertEquals("", props.getBaseUrl());
                });
    }

    @Test
    void bindsUsernameAndPassword() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "config-service.base-url=http://localhost",
                        "config-service.username=svc-user",
                        "config-service.password=svc-pass"
                )
                .run(context -> {
                    var props = context.getBean(ConfigServiceProperties.class);
                    assertEquals("svc-user", props.getUsername());
                    assertEquals("svc-pass", props.getPassword());
                });
    }

    @Test
    void defaultUsernameAndPasswordAreEmpty() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    var props = context.getBean(ConfigServiceProperties.class);
                    assertEquals("", props.getUsername());
                    assertEquals("", props.getPassword());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConfigServiceProperties.class)
    static class TestConfig {
    }
}
