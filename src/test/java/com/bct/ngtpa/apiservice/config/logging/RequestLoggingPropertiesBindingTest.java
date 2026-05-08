package com.bct.ngtpa.apiservice.config.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class RequestLoggingPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultValues() {
        contextRunner.run(context -> {
            RequestLoggingProperties props = context.getBean(RequestLoggingProperties.class);
            assertTrue(props.isEnabled());
            assertFalse(props.isLogHeaders());
            assertEquals(List.of("User-Agent", "Accept", "Content-Type"), props.getHeaderAllowlist());
            assertFalse(props.getBodyLogging().isEnabled());
            assertEquals(4096, props.getBodyLogging().getDefaultMaxBodySizeBytes());
            assertTrue(props.getBodyLogging().getEndpoints().isEmpty());
        });
    }

    @Test
    void bindsTopLevelFieldsFromYaml() {
        contextRunner
                .withInitializer(ctx -> loadYaml(ctx, """
                        request-logging:
                          enabled: false
                          log-headers: true
                          header-allowlist:
                            - User-Agent
                            - Authorization
                        """))
                .run(context -> {
                    RequestLoggingProperties props = context.getBean(RequestLoggingProperties.class);
                    assertFalse(props.isEnabled());
                    assertTrue(props.isLogHeaders());
                    assertEquals(List.of("User-Agent", "Authorization"), props.getHeaderAllowlist());
                });
    }

    @Test
    void bindsBodyLoggingSection() {
        contextRunner
                .withInitializer(ctx -> loadYaml(ctx, """
                        request-logging:
                          body-logging:
                            enabled: true
                            default-max-body-size-bytes: 2048
                        """))
                .run(context -> {
                    RequestLoggingProperties props = context.getBean(RequestLoggingProperties.class);
                    assertTrue(props.getBodyLogging().isEnabled());
                    assertEquals(2048, props.getBodyLogging().getDefaultMaxBodySizeBytes());
                });
    }

    @Test
    void bindsEndpointRules() {
        contextRunner
                .withInitializer(ctx -> loadYaml(ctx, """
                        request-logging:
                          body-logging:
                            enabled: true
                            default-max-body-size-bytes: 4096
                            endpoints:
                              - method: POST
                                path-pattern: /api/v1/some-endpoint
                                log-request-body: true
                                log-response-body: false
                                max-body-size-bytes: 1024
                              - method: GET
                                path-pattern: /api/v1/other
                                log-request-body: false
                                log-response-body: true
                        """))
                .run(context -> {
                    RequestLoggingProperties props = context.getBean(RequestLoggingProperties.class);
                    List<RequestLoggingProperties.EndpointRule> endpoints = props.getBodyLogging().getEndpoints();
                    assertEquals(2, endpoints.size());

                    RequestLoggingProperties.EndpointRule first = endpoints.get(0);
                    assertEquals("POST", first.getMethod());
                    assertEquals("/api/v1/some-endpoint", first.getPathPattern());
                    assertTrue(first.isLogRequestBody());
                    assertFalse(first.isLogResponseBody());
                    assertEquals(1024, first.getMaxBodySizeBytes());

                    RequestLoggingProperties.EndpointRule second = endpoints.get(1);
                    assertEquals("GET", second.getMethod());
                    assertEquals("/api/v1/other", second.getPathPattern());
                    assertFalse(second.isLogRequestBody());
                    assertTrue(second.isLogResponseBody());
                    assertNull(second.getMaxBodySizeBytes());
                });
    }

    private static void loadYaml(
            org.springframework.context.ConfigurableApplicationContext ctx, String yaml) {
        var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
        try {
            var sources = new YamlPropertySourceLoader().load("testYaml", resource);
            sources.forEach(s -> ctx.getEnvironment().getPropertySources().addLast(s));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to load YAML", ex);
        }
    }

    @Test
    void baseApplicationYmlHasBodyLoggingDisabledByDefault() {
        // Verifies that the base application.yml explicitly keeps body logging disabled.
        // Body logging must remain opt-in (local/lower environments only) to protect PII.
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withInitializer(ctx -> {
                    var resource = new ClassPathResource("application.yml");
                    try {
                        var sources = new YamlPropertySourceLoader().load("application", resource);
                        sources.forEach(s -> ctx.getEnvironment().getPropertySources().addLast(s));
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Failed to load application.yml", ex);
                    }
                })
                .run(context -> {
                    RequestLoggingProperties props = context.getBean(RequestLoggingProperties.class);
                    assertFalse(props.getBodyLogging().isEnabled(),
                            "Body logging must be disabled in the base application.yml to protect PII. " +
                            "Enable only in local/non-production profiles.");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RequestLoggingProperties.class)
    static class TestConfig {
    }
}
