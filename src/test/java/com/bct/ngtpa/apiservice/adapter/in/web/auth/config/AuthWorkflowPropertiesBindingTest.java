package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWorkflowPropertiesBindingTest {

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
    void bindsRegistrationDefaultsFromApplicationYaml() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthWorkflowProperties.class);
            assertNotNull(properties);
            assertNotNull(properties.getRegistration());

            var registration = properties.getRegistration();
            assertNotNull(registration.getPreStartChallenge());
            assertTrue(registration.getPreStartChallenge().isEnabled());
            assertEquals(AuthWorkflowProperties.PreStartChallengeType.CAPTCHA,
                    registration.getPreStartChallenge().getType());

            assertNotNull(registration.getCaptcha());
            assertEquals(7, registration.getCaptcha().getLength());
            assertEquals("23456789ABCDEFGHJKLMNPQSTUVWXYZ",
                    registration.getCaptcha().getAlphabet());
            assertFalse(registration.getCaptcha().isCaseSensitive());
            assertEquals(300, registration.getCaptcha().getTtlSeconds());
            assertEquals(5, registration.getCaptcha().getMaxAttempts());

            assertNotNull(registration.getOtp());
            assertEquals(6, registration.getOtp().getLength());
            assertTrue(registration.getOtp().isNumericOnly());
            assertEquals(300, registration.getOtp().getTtlSeconds());
            assertEquals(60, registration.getOtp().getResendCooldownSeconds());
            assertEquals(5, registration.getOtp().getMaxVerifyAttempts());
            assertEquals(5, registration.getOtp().getMaxResendAttempts());
            assertTrue(registration.getOtp().isInvalidatePreviousOnResend());
            assertFalse(registration.getOtp().isResetVerifyAttemptsOnResend());
            assertEquals("Your BCT verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                    registration.getOtp().getSmsTemplate());
            assertNotNull(registration.getOtp().getEmailTemplate());
            assertEquals("BCT verification code", registration.getOtp().getEmailTemplate().getSubject());
            assertEquals("Your verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                    registration.getOtp().getEmailTemplate().getBody());

            assertNotNull(registration.getUsername());
            assertEquals(4, registration.getUsername().getMinLength());
            assertEquals(128, registration.getUsername().getMaxLength());
            assertTrue(registration.getUsername().isNoSpaces());
            assertTrue(registration.getUsername().isCaseInsensitive());
            assertTrue(registration.getUsername().isRequiredDigit());
            assertFalse(registration.getUsername().isRequiredSpecial());
            // assertEquals("!@#$%^&*()_-", registration.getUsername().getAllowedSpecialChars());

            assertNotNull(registration.getPassword());
            assertEquals(8, registration.getPassword().getMinLength());
            assertEquals(16, registration.getPassword().getMaxLength());
            assertTrue(registration.getPassword().isNoSpaces());
            assertTrue(registration.getPassword().isNotSameAsUsernameIgnoreCase());
            assertEquals(3, registration.getPassword().getRequiredCategories());
            assertEquals("!@#$%^&*()_-", registration.getPassword().getSpecialChars());
        });
    }

    @Test
    void missingRegistrationDoesNotCreateImplicitDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    var properties = context.getBean(AuthWorkflowProperties.class);
                    assertNull(properties.getRegistration());
                });
    }


    @Test
    void authWorkflowPropertiesBindsRegistrationRateLimitPolicy() {
        contextRunner.run(context -> {
            var rateLimit = context.getBean(AuthWorkflowProperties.class)
                    .getRegistration()
                    .getRateLimit();

            assertNotNull(rateLimit);
            assertNotNull(rateLimit.getRegistrationStart());
            assertNotNull(rateLimit.getRegistrationStart().getByIp());
            assertTrue(rateLimit.getRegistrationStart().getByIp().isEnabled());
            assertEquals(10, rateLimit.getRegistrationStart().getByIp().getMaxAttempts());
            assertEquals(600, rateLimit.getRegistrationStart().getByIp().getWindowSeconds());

            assertNotNull(rateLimit.getOtpSend());
            assertNotNull(rateLimit.getOtpSend().getByDestinationHash());
            assertTrue(rateLimit.getOtpSend().getByDestinationHash().isEnabled());
            assertEquals(3, rateLimit.getOtpSend().getByDestinationHash().getMaxAttempts());
            assertEquals(600, rateLimit.getOtpSend().getByDestinationHash().getWindowSeconds());

            assertNotNull(rateLimit.getOtpVerify());
            assertNotNull(rateLimit.getOtpVerify().getByRegistrationSession());
            assertTrue(rateLimit.getOtpVerify().getByRegistrationSession().isEnabled());
            assertEquals(5, rateLimit.getOtpVerify().getByRegistrationSession().getMaxAttempts());
            assertEquals(300, rateLimit.getOtpVerify().getByRegistrationSession().getWindowSeconds());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {
    }
}