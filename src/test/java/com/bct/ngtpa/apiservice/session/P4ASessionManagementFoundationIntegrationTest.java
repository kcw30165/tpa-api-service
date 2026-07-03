package com.bct.ngtpa.apiservice.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.XsrfCookiePolicyMapper;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.service.session.SecureSessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

class P4ASessionManagementFoundationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                try {
                    var sources = new YamlPropertySourceLoader()
                            .load("applicationConfig", new ClassPathResource("application.yml"));
                    sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void p4aFoundationBindsSessionIdPolicy() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthFlowSessionProperties.class);
            assertThat(properties.getSessionId().getPrefix()).isEqualTo("afs");
            assertThat(properties.getSessionId().getEntropyBytes()).isEqualTo(16);

            var generator = new SecureSessionIdGenerator();
            assertThat(generator.generate(new SessionIdGenerationCommand("login", 16)).value())
                    .startsWith("login_");
        });
    }

    @Test
    void p4aSessionIdGeneratorUsesConfiguredPrefix() {
        var generator = new SecureSessionIdGenerator();

        var authFlowId = generator.generate(new SessionIdGenerationCommand("afs", 16)).value();
        var loginId = generator.generate(new SessionIdGenerationCommand("login", 16)).value();

        assertThat(authFlowId).startsWith("afs_").matches("^[A-Za-z0-9_-]+$");
        assertThat(loginId).startsWith("login_").matches("^[A-Za-z0-9_-]+$");
        assertThat(authFlowId).isNotBlank().doesNotContainPattern("\\s");
        assertThat(loginId).isNotBlank().doesNotContainPattern("\\s");
    }

    @Test
    void p4aGeneratedSessionIdDoesNotExposeCookieNameScenarioOrPii() {
        var id = new SecureSessionIdGenerator()
                .generate(new SessionIdGenerationCommand("afs", 16))
                .value();

        assertThat(id)
                .doesNotContain("auth_flow_sid")
                .doesNotContain("XSRF-TOKEN")
                .doesNotContain("REGISTRATION")
                .doesNotContain("LOGIN")
                .doesNotContain("FORGOT_PASSWORD")
                .doesNotContain("USERNAME_RECOVERY")
                .doesNotContain("member@example.com")
                .doesNotContain("91234567")
                .doesNotContain("policy")
                .doesNotContain("cert");
    }

    @Test
    void p4aTtlPolicyComputesExpectedExpiry() {
        var now = Instant.parse("2026-06-29T00:00:00Z");
        var policy = new SessionTtlPolicy(true, true, 1800);

        assertThat(new SessionTtlCalculator().expiresAt(now, policy))
                .isEqualTo(Instant.parse("2026-06-29T00:30:00Z"));
    }

    @Test
    void p4aCookiePoliciesRepresentAuthFlowAndXsrfCookies() {
        var authFlowCookie = new SessionCookiePolicy(
                "auth_flow_sid", "/api/v1/auth", 1800, true, true, SessionCookieSameSite.NONE);
        var xsrfCookie = new SessionCookiePolicy(
                "XSRF-TOKEN", "/api/v1/auth", 1800, false, true, SessionCookieSameSite.NONE);

        assertThat(authFlowCookie.name()).isEqualTo("auth_flow_sid");
        assertThat(authFlowCookie.httpOnly()).isTrue();
        assertThat(authFlowCookie.secure()).isTrue();
        assertThat(authFlowCookie.sameSite()).isEqualTo(SessionCookieSameSite.NONE);

        assertThat(xsrfCookie.name()).isEqualTo("XSRF-TOKEN");
        assertThat(xsrfCookie.httpOnly()).isFalse();
        assertThat(xsrfCookie.secure()).isTrue();
        assertThat(xsrfCookie.sameSite()).isEqualTo(SessionCookieSameSite.NONE);
    }

    @Test
    void p4aCookieInstructionToStringDoesNotExposeCookieValue() {
        var authInstruction = new SessionCookieInstruction(
                SessionCookieOperation.SET,
                new SessionCookiePolicy("auth_flow_sid", "/api/v1/auth", 1800, true, true,
                        SessionCookieSameSite.NONE),
                "afs_secret_cookie_value");
        var xsrfInstruction = new SessionCookieInstruction(
                SessionCookieOperation.SET,
                new SessionCookiePolicy("XSRF-TOKEN", "/api/v1/auth", 1800, false, true,
                        SessionCookieSameSite.NONE),
                "csrf_secret_token_value");

        assertThat(authInstruction.toString()).doesNotContain("afs_secret_cookie_value");
        assertThat(xsrfInstruction.toString()).doesNotContain("csrf_secret_token_value");
    }

    @Test
    void p4aXsrfTransportPolicyUsesConfiguredHeaderName() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthFlowSessionProperties.class);
            var transportPolicy = new XsrfCookiePolicyMapper().map(properties.getCsrf());

            assertThat(transportPolicy.headerName()).isEqualTo("X-XSRF-TOKEN");
            assertThat(transportPolicy.cookiePolicy().name()).isEqualTo("XSRF-TOKEN");
            assertThat(transportPolicy.cookiePolicy().httpOnly()).isFalse();
        });
    }

    @Test
    void p4aDoesNotDuplicateCorsAllowedOrigins() {
        contextRunner
                .withPropertyValues("cors.allowed-origins=https://portal.example.com")
                .run(context -> {
                    var authFlow = context.getBean(AuthFlowSessionProperties.class);
                    var cors = context.getBean(CorsProperties.class);

                    assertThat(fieldNames(authFlow.getClass())).doesNotContain("cors", "allowedOrigins", "allowCredentials");
                    assertThat(Arrays.stream(authFlow.getClass().getDeclaredClasses()).map(Class::getSimpleName))
                            .doesNotContain("Cors", "CorsPolicy");
                    assertThat(fieldNames(authFlow.getOriginValidation().getClass()))
                            .doesNotContain("allowedOrigins", "allowCredentials");
                    assertThat(cors.getAllowedOrigins()).containsExactly("https://portal.example.com");
                });
    }

    @Test
    void p4aDoesNotAllowWildcardOriginWithCredentialedCors() {
        var corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("*"));
        corsProperties.setAllowedHeaders(List.of("Content-Type"));
        corsProperties.setAllowedMethods(List.of("GET", "POST"));

        var source = new SecurityConfig().corsConfigurationSource(corsProperties);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("/api/v1/auth/registration/start")
                .header("Origin", "https://portal.example.com")
                .build());

        assertThat(source.getCorsConfiguration(exchange)).isNull();
    }

    @Test
    void p4aReadmeDocumentsFoundationOnlySection() throws Exception {
        var readme = Files.readString(Path.of("README.md"));

        assertThat(readme).contains("### Session Management Foundation");
        assertThat(readme).contains("P4A adds reusable session-management foundation components");
        assertThat(readme).contains("`auth_flow_sid` is HttpOnly, Secure, SameSite=None, Path=/api/v1/auth");
        assertThat(readme).contains("`XSRF-TOKEN` is Secure, SameSite=None, Path=/api/v1/auth, and intentionally not HttpOnly");
        assertThat(readme).contains("existing `CorsProperties` remains the single source of truth");
        assertThat(readme).contains("Not implemented in P4A");
        assertThat(readme).contains("AuthFlowSession Redis persistence");
        assertThat(readme).contains("registration endpoints");
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AuthFlowSessionProperties.class, CorsProperties.class})
    static class TestConfig {
    }
}
