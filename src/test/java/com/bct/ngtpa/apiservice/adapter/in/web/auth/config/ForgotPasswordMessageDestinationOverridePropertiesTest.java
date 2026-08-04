package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ForgotPasswordMessageDestinationOverridePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsEnvironmentBackedMobileAndEmailDestinations() {
        contextRunner
                .withPropertyValues(
                        "auth-workflows.forgot-password.message.destination-override.mobile= 91234567 ",
                        "auth-workflows.forgot-password.message.destination-override.email= forgot-password-test@example.com ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var override = override(context.getBean(AuthWorkflowProperties.class));

                    override.validateRequiredDestinations("auth-workflows.forgot-password.message.destination-override");
                    assertThat(override.requiredMobile("auth-workflows.forgot-password.message.destination-override"))
                            .isEqualTo("91234567");
                    assertThat(override.requiredEmail("auth-workflows.forgot-password.message.destination-override"))
                            .isEqualTo("forgot-password-test@example.com");
                });
    }

    @Test
    void missingEmailDestination_isRejectedForForgotPasswordPrefix() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setMobile("91234567");

        assertThatThrownBy(
                () -> override.validateRequiredDestinations("auth-workflows.forgot-password.message.destination-override"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-workflows.forgot-password.message.destination-override")
                .hasMessageContaining("email");
    }

    private AuthWorkflowProperties.DestinationOverride override(AuthWorkflowProperties properties) {
        assertThat(properties.getForgotPassword()).isNotNull();
        assertThat(properties.getForgotPassword().getMessage()).isNotNull();
        return properties.getForgotPassword().getMessage().getDestinationOverride();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {
    }
}