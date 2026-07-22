package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.ConfigBackedAuthWorkflowPolicyAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Characterizes the shared explicit resend-limit switch without adding test constructors. */
class Task0043OtpResendLimitPolicyTest {

    @Test
    void registrationBindsExplicitFiniteResendLimit() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml)
                .contains("resend-limit-enabled: true")
                .contains("max-resend-attempts: 5");

        AuthWorkflowProperties properties = new AuthWorkflowProperties();
        AuthWorkflowProperties.Registration registration = new AuthWorkflowProperties.Registration();
        registration.getOtp().setResendLimitEnabled(true);
        registration.getOtp().setMaxResendAttempts(5);
        properties.setRegistration(registration);

        var policy = new ConfigBackedAuthWorkflowPolicyAdapter(properties)
                .registrationPolicy()
                .otp();
        assertThat(policy.resendLimitEnabled()).isTrue();
        assertThat(policy.maxResendAttempts()).isEqualTo(5);
    }

    @Test
    void sharedChallengeServiceRepresentsDisabledLimitWithoutMagicLargeThreshold() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/otp/DefaultOtpChallengeAdapter.java"));
        assertThat(source)
                .contains("challenge.maxResendAttempts() >= 0")
                .doesNotContain("99999")
                .doesNotContain("Integer.MAX_VALUE");
    }

    @Test
    void registrationAdapterKeepsLimitEnabledAndMapsDisabledPolicyToUnlimitedSentinel() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/RegistrationOtpChallengeAdapter.java"));
        assertThat(source).contains(
                "policy.resendLimitEnabled() ? policy.maxResendAttempts() : -1");
    }
}
