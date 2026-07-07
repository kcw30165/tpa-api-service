package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWorkflowPolicyResolverTest {

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
    void registrationPolicyReturnsConfiguredPreStartChallenge() {
        contextRunner.run(context -> {
            var policy = context.getBean(AuthWorkflowPolicyPort.class).registrationPolicy();

            assertTrue(policy.preStartChallenge().enabled());
            assertEquals(PreStartChallengeType.CAPTCHA, policy.preStartChallenge().type());
            assertEquals(7, policy.captcha().length());
            assertTrue(policy.captcha().caseSensitive());
            assertEquals(300, policy.captcha().ttlSeconds());
            assertEquals(5, policy.captcha().maxAttempts());
        });
    }

    @Test
    void registrationPolicyReturnsConfiguredOtpPolicy() {
        contextRunner.run(context -> {
            var otp = context.getBean(AuthWorkflowPolicyPort.class).registrationPolicy().otp();

            assertEquals(6, otp.length());
            assertTrue(otp.numericOnly());
            assertEquals(300, otp.ttlSeconds());
            assertEquals(60, otp.resendCooldownSeconds());
            assertEquals(5, otp.maxVerifyAttempts());
            assertEquals(5, otp.maxResendAttempts());
            assertTrue(otp.invalidatePreviousOnResend());
            assertFalse(otp.resetVerifyAttemptsOnResend());
        });
    }

    @Test
    void registrationPolicyReturnsConfiguredUsernameAndPasswordPolicies() {
        contextRunner.run(context -> {
            var policy = context.getBean(AuthWorkflowPolicyPort.class).registrationPolicy();

            assertEquals(4, policy.username().minLength());
            assertEquals(128, policy.username().maxLength());
            assertTrue(policy.username().noSpaces());
            assertTrue(policy.username().caseInsensitive());
            assertTrue(policy.username().requiredDigit());
            assertTrue(policy.username().requiredSpecial());
            assertEquals("!@#$%^&*()_-", policy.username().allowedSpecialChars());

            assertEquals(8, policy.password().minLength());
            assertEquals(16, policy.password().maxLength());
            assertTrue(policy.password().noSpaces());
            assertTrue(policy.password().notSameAsUsernameIgnoreCase());
            assertEquals(3, policy.password().requiredCategories());
            assertEquals("!@#$%^&*()_-", policy.password().specialChars());
        });
    }

    @Test
    void resolveIdentityTypeSearchScopeRejectsBlankUnknownOrNull() {
        contextRunner.run(context -> {
            var port = context.getBean(AuthWorkflowPolicyPort.class);

            assertEquals(Optional.empty(), port.resolveIdentityTypeSearchScope(null));
            assertEquals(Optional.empty(), port.resolveIdentityTypeSearchScope("   "));
            assertEquals(Optional.empty(), port.resolveIdentityTypeSearchScope("UNKNOWN"));
        });
    }


    @Test
    void authWorkflowPolicyExposesRegistrationRateLimitPolicy() {
        contextRunner.run(context -> {
            var rateLimit = context.getBean(AuthWorkflowPolicyPort.class)
                    .registrationPolicy()
                    .rateLimit();

            assertNotNull(rateLimit);
            assertTrue(rateLimit.registrationStartByIp().enabled());
            assertEquals(10, rateLimit.registrationStartByIp().maxAttempts());
            assertEquals(600, rateLimit.registrationStartByIp().windowSeconds());

            assertTrue(rateLimit.otpSendByDestinationHash().enabled());
            assertEquals(3, rateLimit.otpSendByDestinationHash().maxAttempts());
            assertEquals(600, rateLimit.otpSendByDestinationHash().windowSeconds());

            assertTrue(rateLimit.otpVerifyByRegistrationSession().enabled());
            assertEquals(5, rateLimit.otpVerifyByRegistrationSession().maxAttempts());
            assertEquals(300, rateLimit.otpVerifyByRegistrationSession().windowSeconds());
        });
    }

    @Test
    void rateLimitPolicyIsDirectConfigOnly() throws Exception {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/config/ConfigBackedAuthWorkflowPolicyAdapter.java"));

        assertFalse(adapter.contains("ConfigVariantCandidateGenerator"));
        assertFalse(adapter.contains("DefaultConfigVariantResolver"));
        assertFalse(adapter.contains("accountEnv"));
        assertFalse(adapter.contains("trustCode"));
        assertFalse(adapter.contains("schemeType"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {

        @Bean
        AuthWorkflowPolicyPort authWorkflowPolicyPort(AuthWorkflowProperties properties) {
            return new ConfigBackedAuthWorkflowPolicyAdapter(properties);
        }
    }
}