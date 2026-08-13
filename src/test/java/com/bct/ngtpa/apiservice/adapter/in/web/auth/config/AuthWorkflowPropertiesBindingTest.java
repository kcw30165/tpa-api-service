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
            assertTrue(registration.getOtp().isResetVerifyAttemptsOnResend());
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
            // assertEquals("!@#$%^&*()_-",
            // registration.getUsername().getAllowedSpecialChars());

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
    void authWorkflowPropertiesBindsOtpLifecyclePolicy() {
        contextRunner.run(context -> {
            var otp = context.getBean(AuthWorkflowProperties.class)
                    .getRegistration()
                    .getOtp();

            assertNotNull(otp);
            assertEquals(5, otp.getMaxResendAttempts());
            assertEquals(5, otp.getMaxVerifyAttempts());
            assertEquals(60, otp.getResendCooldownSeconds());
            assertTrue(otp.isInvalidatePreviousOnResend());
        });
    }

    @Test
    void bindsForgotPasswordDefaultsFromApplicationYaml() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthWorkflowProperties.class);

            assertNotNull(properties.getForgotPassword());
            assertEquals(4, properties.getForgotPassword().getUsername().getMinLength());
            assertEquals(128, properties.getForgotPassword().getUsername().getMaxLength());
            assertTrue(properties.getForgotPassword().getUsername().isTrim());
            assertTrue(properties.getForgotPassword().getUsername().isCaseInsensitive());

            assertEquals(4, properties.getForgotPassword().getAllowedPublicIdentityTypes().size());
            assertTrue(properties.getForgotPassword().getAllowedPublicIdentityTypes().contains("GPN"));
            assertTrue(properties.getForgotPassword().getAllowedPublicIdentityTypes().contains("GPFS"));
            assertTrue(properties.getForgotPassword().getAllowedPublicIdentityTypes().contains("STAFF_NO"));
            assertTrue(properties.getForgotPassword().getAllowedPublicIdentityTypes().contains("ID"));
            assertEquals("GPN", properties.getForgotPassword().getTasToPublicIdentityTypeMapping().get("GPN"));
            assertEquals("GPFS", properties.getForgotPassword().getTasToPublicIdentityTypeMapping().get("GPFS"));
            assertEquals("ID", properties.getForgotPassword().getTasToPublicIdentityTypeMapping().get("ID"));
            assertEquals("STAFF_NO",
                    properties.getForgotPassword().getTasToPublicIdentityTypeMapping().get("STAFFNO"));

            var otp = properties.getForgotPassword().getOtp();
            assertEquals(6, otp.getLength());
            assertTrue(otp.isNumericOnly());
            assertEquals(300, otp.getTtlSeconds());
            assertEquals(60, otp.getResendCooldownSeconds());
            assertEquals(5, otp.getMaxVerifyAttempts());
            assertTrue(otp.isResendLimitEnabled());
            assertEquals(5, otp.getMaxResendAttempts());
            assertTrue(otp.isInvalidatePreviousOnResend());
            assertTrue(otp.isResetVerifyAttemptsOnResend());
            // assertEquals("Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
            //         otp.getSmsTemplate());
            // assertEquals("BCT forgot password verification code",
            //         otp.getEmailTemplate().getSubject());
            // assertEquals("Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
            //         otp.getEmailTemplate().getBody());

            var idempotency = properties.getForgotPassword().getIdempotency();
            assertEquals(300, idempotency.getStartTtlSeconds());
            assertEquals(300, idempotency.getDetailsAndOtpTtlSeconds());
            assertEquals(300, idempotency.getOtpResendTtlSeconds());
            assertEquals(300, idempotency.getOtpVerifyTtlSeconds());
            assertEquals(300, idempotency.getCompleteTtlSeconds());
            assertEquals(300, idempotency.getCancelTtlSeconds());

            assertEquals(300, properties.getForgotPassword().getResetAuthorization().getTtlSeconds());
        });
    }

    @Test
    void bindsUsernameRecoveryDefaultsFromApplicationYaml() {
        contextRunner.run(context -> {
            var properties = context.getBean(AuthWorkflowProperties.class);

            assertNotNull(properties.getUsernameRecovery());
            assertEquals(600, properties.getUsernameRecovery().getSession().getTtlSeconds());
            assertNotNull(properties.getUsernameRecovery().getMessage());
            assertNotNull(properties.getUsernameRecovery().getMessage().getDestinationOverride());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {
    }
}