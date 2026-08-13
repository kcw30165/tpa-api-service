package com.bct.ngtpa.apiservice.adapter.in.web.session.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

class AuthFlowSessionPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var resource = new ClassPathResource("application.yml");
                try {
                    var propertySources = new YamlPropertySourceLoader().load("applicationConfig", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void bindsAuthFlowSessionCookiePolicy() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthFlowSessionProperties.class);
            assertNotNull(properties);

            var cookie = properties.getCookie();
            assertNotNull(cookie);
            assertEquals("auth_flow_sid", cookie.getName());
            assertEquals("/api/v1/auth", cookie.getPath());
            assertEquals(1800, cookie.getMaxAgeSeconds());
            assertTrue(cookie.isHttpOnly());
            assertTrue(cookie.isSecure());
            assertEquals("None", cookie.getSameSite());
        });
    }

    @Test
    void bindsAuthFlowSessionCsrfPolicy() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthFlowSessionProperties.class);
            var csrf = properties.getCsrf();
            assertNotNull(csrf);
            assertEquals("XSRF-TOKEN", csrf.getCookieName());
            assertEquals("X-XSRF-TOKEN", csrf.getHeaderName());
            assertEquals("/api/v1/auth", csrf.getCookiePath());
            assertEquals(1800, csrf.getMaxAgeSeconds());
            assertTrue(csrf.isSecure());
            assertEquals("None", csrf.getSameSite());

            assertThat(fieldNames(csrf.getClass()))
                    .as("Angular-readable XSRF-TOKEN config must not model an HttpOnly=true policy")
                    .doesNotContain("httpOnly");
        });
    }

    @Test
    void authFlowSessionDoesNotDuplicateCorsAllowedOrigins() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "cors.allowed-origins=https://portal.example.com",
                        "cors.allowed-headers=Content-Type,X-Request-Id",
                        "cors.allowed-methods=GET,POST",
                        "auth-flow-session.cookie.name=auth_flow_sid")
                .run(context -> {
                    var authFlow = context.getBean(AuthFlowSessionProperties.class);
                    var cors = context.getBean(CorsProperties.class);

                    assertThat(fieldNames(authFlow.getClass())).doesNotContain("cors");
                    assertThat(Arrays.stream(authFlow.getClass().getDeclaredClasses()).map(Class::getSimpleName))
                            .doesNotContain("Cors", "CorsPolicy");
                    assertEquals(List.of("https://portal.example.com"), cors.getAllowedOrigins());
                });
    }

    @Test
    void existingCorsPropertiesRejectOrAvoidWildcardWithCredentials() {
        var corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("*"));
        corsProperties.setAllowedHeaders(List.of("Content-Type"));
        corsProperties.setAllowedMethods(List.of("GET", "POST"));

        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(corsProperties);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("/api/v1/auth/registration/start")
                .header("Origin", "https://portal.example.com")
                .build());

        CorsConfiguration configuration = source.getCorsConfiguration(exchange);
        assertThat(configuration)
                .as("Wildcard origin must not be registered with credentialed CORS")
                .isNull();
    }

    @Test
    void bindsOriginValidationPolicy() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthFlowSessionProperties.class);
            var originValidation = properties.getOriginValidation();
            assertNotNull(originValidation);
            assertTrue(originValidation.isEnabled());
            assertThat(fieldNames(originValidation.getClass()))
                    .as("Origin/Referer validation must reuse CorsProperties, not duplicate allowed origins")
                    .doesNotContain("allowedOrigins", "allowCredentials");
        });
    }

    @Test
    void bindsSingleActiveFlowConcurrencyPolicy() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthFlowSessionProperties.class);
            var concurrency = properties.getConcurrency();
            assertNotNull(concurrency);
            assertEquals(AuthFlowSessionProperties.AuthFlowConcurrencyMode.SINGLE_ACTIVE_FLOW,
                    concurrency.getMode());
            assertEquals(AuthFlowSessionProperties.AuthFlowOnNewFlowPolicy.REPLACE_EXISTING,
                    concurrency.getOnNewFlow());
        });
    }

    @Test
    void bindsScenarioTtlPolicies() {
        contextRunner.run(context -> {
            var scenarios = context.getBean(AuthFlowSessionProperties.class).getScenarios();
            assertScenario(scenarios.getRegistration(), true, true, 1800);
            assertScenario(scenarios.getLogin(), true, true, 900);
            assertScenario(scenarios.getForgotPassword(), true, true, 1800);
            assertScenario(scenarios.getUsernameRecovery(), true, false, 600);
        });
    }

    @Test
    void authFlowSessionPropertiesDoNotDependOnApplicationDomainOrRedis() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.bct.ngtpa.apiservice.adapter.in.web.session.config");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..adapter.in.web.session.config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.lettuce..",
                        "org.springframework.data.redis..",
                        "com.bct.ngtpa.apiservice.application.usecase..",
                        "com.bct.ngtpa.apiservice.adapter.out.redis..")
                .check(classes);
    }

    private static void assertScenario(AuthFlowSessionProperties.Scenario scenario,
            boolean enabled,
            boolean required,
            int ttlSeconds) {
        assertNotNull(scenario);
        assertEquals(enabled, scenario.isEnabled());
        assertEquals(required, scenario.isRequired());
        assertEquals(ttlSeconds, scenario.getTtlSeconds());
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .toList();
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ AuthFlowSessionProperties.class, CorsProperties.class })
    static class TestConfig {
    }
}
