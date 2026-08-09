package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Task 016 tests for the dedicated Username Recovery message destination override properties.
 *
 * <p>Verifies environment-backed binding, per-prefix fail-closed validation, and that
 * configuration errors disclose property names only, never configured values or member
 * destinations.</p>
 */
class UsernameRecoveryMessageDestinationOverridePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsEnvironmentBackedMobileAndEmailDestinations() {
        contextRunner
                .withPropertyValues(
                        "auth-workflows.username-recovery.message.destination-override.mobile= 91234567 ",
                        "auth-workflows.username-recovery.message.destination-override.email= username-recovery-test@example.com ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var override = override(context.getBean(AuthWorkflowProperties.class));

                    override.validateRequiredDestinations(
                            "auth-workflows.username-recovery.message.destination-override");
                    assertThat(override.requiredMobile(
                            "auth-workflows.username-recovery.message.destination-override"))
                            .isEqualTo("91234567");
                    assertThat(override.requiredEmail(
                            "auth-workflows.username-recovery.message.destination-override"))
                            .isEqualTo("username-recovery-test@example.com");
                });
    }

    @Test
    void missingEmailDestination_isRejectedForUsernameRecoveryPrefix() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setMobile("91234567");

        assertThatThrownBy(
                () -> override.validateRequiredDestinations(
                        "auth-workflows.username-recovery.message.destination-override"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-workflows.username-recovery.message.destination-override")
                .hasMessageContaining("email")
                .hasMessageNotContaining("91234567");
    }

    @Test
    void missingMobileDestination_isRejectedForUsernameRecoveryPrefix() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setEmail("username-recovery-test@example.com");

        assertThatThrownBy(
                () -> override.validateRequiredDestinations(
                        "auth-workflows.username-recovery.message.destination-override"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-workflows.username-recovery.message.destination-override")
                .hasMessageContaining("mobile")
                .hasMessageNotContaining("username-recovery-test@example.com");
    }

    @Test
    void validationErrorDisclosesPropertyNamesOnlyAndNeverConfiguredValues() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setMobile("+852-sensitive-member-mobile");

        assertThatThrownBy(
                () -> override.requiredEmail(
                        "auth-workflows.username-recovery.message.destination-override"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "auth-workflows.username-recovery.message.destination-override.email must not be blank")
                .hasMessageNotContaining("+852-sensitive-member-mobile");
    }

    private AuthWorkflowProperties.DestinationOverride override(AuthWorkflowProperties properties) {
        assertThat(properties.getUsernameRecovery()).isNotNull();
        assertThat(properties.getUsernameRecovery().getMessage()).isNotNull();
        return properties.getUsernameRecovery().getMessage().getDestinationOverride();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {
    }
}
