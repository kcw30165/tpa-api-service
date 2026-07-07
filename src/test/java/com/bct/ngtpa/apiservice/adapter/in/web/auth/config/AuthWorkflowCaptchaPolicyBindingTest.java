package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class AuthWorkflowCaptchaPolicyBindingTest {

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var resource = new ClassPathResource("application.yml");
                try {
                    var propertySources = new YamlPropertySourceLoader().load("applicationConfig", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Failed to load YAML", exception);
                }
            });

    @Test
    void captchaPolicyIsReadFromExistingAuthWorkflowProperties() {
        baseRunner.run(context -> {
            var policy = context.getBean(AuthWorkflowPolicyPort.class).registrationPolicy();

            assertThat(policy.captcha().length()).isEqualTo(7);
            assertThat(policy.captcha().alphabet()).isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
            assertThat(policy.captcha().caseSensitive()).isTrue();
            assertThat(policy.captcha().ttlSeconds()).isEqualTo(300);
            assertThat(policy.captcha().maxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void captchaPolicyUsesConfiguredValuesNotHardcodedDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withInitializer(context -> {
                    var yaml = """
                            auth-workflows:
                              registration:
                                pre-start-challenge:
                                  enabled: true
                                  type: CAPTCHA
                                captcha:
                                  length: 8
                                  alphabet: ABC123
                                  case-sensitive: false
                                  ttl-seconds: 123
                                  max-attempts: 9
                            """;
                    try {
                        var propertySources = new YamlPropertySourceLoader().load("testConfig", new ByteArrayResource(yaml.getBytes()) {
                            @Override
                            public String getFilename() {
                                return "auth-workflow-captcha-test.yml";
                            }
                        });
                        propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("Failed to load test YAML", exception);
                    }
                })
                .run(context -> {
                    var policy = context.getBean(AuthWorkflowPolicyPort.class).registrationPolicy();
                    assertThat(policy.captcha().length()).isEqualTo(8);
                    assertThat(policy.captcha().alphabet()).isEqualTo("ABC123");
                    assertThat(policy.captcha().caseSensitive()).isFalse();
                    assertThat(policy.captcha().ttlSeconds()).isEqualTo(123);
                    assertThat(policy.captcha().maxAttempts()).isEqualTo(9);
                });
    }

    @Test
    void preStartChallengePolicyBindsFromExistingAuthWorkflowProperties() {
        baseRunner.run(context -> {
            var policy = context.getBean(AuthWorkflowPolicyPort.class).registrationPolicy();

            assertThat(policy.preStartChallenge().enabled()).isTrue();
            assertThat(policy.preStartChallenge().type()).isEqualTo(PreStartChallengeType.CAPTCHA);
        });
    }

    @Test
    void captchaDoesNotIntroduceSeparateCaptchaRootProperties() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/config/AuthWorkflowProperties.java"));
        assertThat(source).contains("@ConfigurationProperties(prefix = \"auth-workflows\")");
        assertThat(source).doesNotContain("@ConfigurationProperties(prefix = \"captcha\")", "@ConfigurationProperties(prefix = \"auth-captcha\")");
    }

    @Configuration
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {
        @Bean
        AuthWorkflowPolicyPort authWorkflowPolicyPort(AuthWorkflowProperties properties) {
            return new ConfigBackedAuthWorkflowPolicyAdapter(properties);
        }
    }
}