package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.workflow.LoginErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowActionCode;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowActionResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Task0043LoginFoundationTest {

    @Test
    void workflowVocabularyContainsLoginStepsAndRestartAction() {
        assertThat(WorkflowNextStep.values()).contains(
                WorkflowNextStep.SELECT_OTP_DESTINATION,
                WorkflowNextStep.SELECT_ACCOUNT,
                WorkflowNextStep.RESTART_FLOW);
        assertThat(WorkflowActionCode.values()).containsExactly(WorkflowActionCode.RESTART_FLOW);
        assertThat(Arrays.stream(WorkflowActionResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("code");
    }

    @Test
    void loginVerificationExhaustionUsesCanonicalPublicErrorKey() {
        assertThat(LoginErrorKey.OTP_VERIFY_ATTEMPTS_EXCEEDED.value())
                .isEqualTo("err.login.otp.verifyAttemptsExceeded");
    }

    @Test
    void loginConfigurationSupportsUnlimitedResendsAndValidatesRequiredPolicy() {
        AuthWorkflowProperties properties = validProperties();
        properties.validateLoginPolicy();
        assertThat(properties.getLogin().getOtp().isResendLimitEnabled()).isFalse();
        assertThat(properties.getLogin().getOtp().getMaxVerifyAttempts()).isEqualTo(5);

        properties.getLogin().getSession().setTtlSeconds(0);
        assertThatThrownBy(properties::validateLoginPolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session.ttl-seconds");
    }

    @Test
    void yamlContainsRequiredLoginPolicyWithoutImplementingEndpoints() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains(
                "login:",
                "resend-limit-enabled: false",
                "max-verify-attempts: 5",
                "idle-timeout-seconds:",
                "identifier-width:");
    }

    private AuthWorkflowProperties validProperties() {
        AuthWorkflowProperties properties = new AuthWorkflowProperties();
        AuthWorkflowProperties.Login login = new AuthWorkflowProperties.Login();
        login.getCredential().setUsernameMinLength(1);
        login.getCredential().setUsernameMaxLength(128);
        login.getCredential().setPasswordMinLength(1);
        login.getCredential().setPasswordMaxLength(128);
        login.getCredential().setUsernameTrim(true);
        login.getCredential().setUsernameNormalization("ASCII_LOWERCASE");
        login.getCredential().setUsernameAllowedPattern("^[A-Za-z0-9._-]+$");
        login.getCredential().setPasswordTrim(false);
        login.getCredential().setPasswordCaseSensitive(true);
        login.getOtp().setLength(6);
        login.getOtp().setTtlSeconds(300);
        login.getOtp().setResendCooldownSeconds(60);
        login.getOtp().setMaxVerifyAttempts(5);
        login.getIdempotency().setStartTtlSeconds(300);
        login.getIdempotency().setOtpSendTtlSeconds(300);
        login.getIdempotency().setOtpResendTtlSeconds(300);
        login.getIdempotency().setOtpVerifyTtlSeconds(300);
        login.getSession().setTtlSeconds(900);
        login.getSession().setIdleTimeoutSeconds(900);
        login.getAccount().setIdentifierWidth(8);
        properties.setLogin(login);
        return properties;
    }
}
