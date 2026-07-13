package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RegistrationMessageDestinationOverridePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsEnvironmentBackedMobileAndEmailDestinations() {
        contextRunner
                .withPropertyValues(
                        "auth-workflows.registration.message.destination-override.mobile= 91234567 ",
                        "auth-workflows.registration.message.destination-override.email= registration-test@example.com ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var override = override(context.getBean(AuthWorkflowProperties.class));

                    override.validateRequiredDestinations();
                    assertThat(override.requiredMobile()).isEqualTo("91234567");
                    assertThat(override.requiredEmail()).isEqualTo("registration-test@example.com");
                });
    }

    @Test
    void missingBothDestinations_reportsPropertyNamesWithoutDestinationValues() {
        var override = new AuthWorkflowProperties.DestinationOverride();

        assertThatThrownBy(override::validateRequiredDestinations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-workflows.registration.message.destination-override")
                .hasMessageContaining("mobile and email");
    }

    @Test
    void missingMobileDestination_isRejected() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setEmail("registration-test@example.com");

        assertThatThrownBy(override::validateRequiredDestinations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mobile")
                .hasMessageNotContaining("registration-test@example.com");
    }

    @Test
    void missingEmailDestination_isRejected() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setMobile("91234567");

        assertThatThrownBy(override::validateRequiredDestinations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email")
                .hasMessageNotContaining("91234567");
    }

    @Test
    void whitespaceOnlyDestinations_areRejected() {
        var override = new AuthWorkflowProperties.DestinationOverride();
        override.setMobile("   ");
        override.setEmail("\t");

        assertThatThrownBy(override::validateRequiredDestinations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mobile and email");
    }

    private AuthWorkflowProperties.DestinationOverride override(AuthWorkflowProperties properties) {
        assertThat(properties.getRegistration()).isNotNull();
        assertThat(properties.getRegistration().getMessage()).isNotNull();
        return properties.getRegistration().getMessage().getDestinationOverride();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthWorkflowProperties.class)
    static class TestConfig {
    }
}
