package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.ConfigBackedAuthWorkflowPolicyAdapter;
import org.junit.jupiter.api.Test;

/**
 * Task 0043 test-only baseline. Characterizes the registration OTP policy before
 * the shared authentication foundation is refactored.
 */
class Task0043RegistrationOtpPolicyCharacterizationTest {

    @Test
    void registrationOtpPolicyRemainsFiniteAndConfigurationBacked() {
        AuthWorkflowProperties properties = new AuthWorkflowProperties();
        AuthWorkflowProperties.Registration registration = new AuthWorkflowProperties.Registration();
        registration.getOtp().setLength(6);
        registration.getOtp().setNumericOnly(true);
        registration.getOtp().setTtlSeconds(300);
        registration.getOtp().setResendCooldownSeconds(60);
        registration.getOtp().setMaxVerifyAttempts(5);
        registration.getOtp().setMaxResendAttempts(5);
        registration.getOtp().setInvalidatePreviousOnResend(true);
        registration.getOtp().setResetVerifyAttemptsOnResend(true);
        registration.getOtp().setSmsTemplate("otp={otp}");
        registration.getOtp().getEmailTemplate().setSubject("OTP");
        registration.getOtp().getEmailTemplate().setBody("otp={otp}");
        properties.setRegistration(registration);

        var policy = new ConfigBackedAuthWorkflowPolicyAdapter(properties)
                .registrationPolicy()
                .otp();

        assertThat(policy.length()).isEqualTo(6);
        assertThat(policy.numericOnly()).isTrue();
        assertThat(policy.ttlSeconds()).isEqualTo(300);
        assertThat(policy.resendCooldownSeconds()).isEqualTo(60);
        assertThat(policy.maxVerifyAttempts()).isEqualTo(5);
        assertThat(policy.maxResendAttempts()).isEqualTo(5);
        assertThat(policy.invalidatePreviousOnResend()).isTrue();
        assertThat(policy.resetVerifyAttemptsOnResend()).isTrue();
        assertThat(1 + policy.maxResendAttempts())
                .as("one initial delivery plus five permitted resends")
                .isEqualTo(6);
    }
}
