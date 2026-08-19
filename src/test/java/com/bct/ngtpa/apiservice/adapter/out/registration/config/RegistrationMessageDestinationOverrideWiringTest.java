package com.bct.ngtpa.apiservice.adapter.out.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationMessageDestinationOverrideAdapter;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RegistrationMessageDestinationOverrideWiringTest {
  private final RegistrationStartGatewayConfig config = new RegistrationStartGatewayConfig();

  @Test
  void nonProductionWithBothDestinations_wiresDecorator() {
    var gateway =
        config.registrationMessageGateway(
            mock(MessageDeliveryPort.class),
            mock(AuthWorkflowPolicyPort.class),
            policy("sit"),
            properties("override-mobile", "override@example.com"));
    assertThat(gateway).isInstanceOf(RegistrationMessageDestinationOverrideAdapter.class);
  }

  @Test
  void nonProductionWithMissingDestination_failsDuringBeanCreation() {
    assertThatThrownBy(
            () ->
                config.registrationMessageGateway(
                    mock(MessageDeliveryPort.class),
                    mock(AuthWorkflowPolicyPort.class),
                    policy("uat"),
                    properties("override-mobile", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("email")
        .hasMessageNotContaining("override-mobile");
  }

  @Test
  void unknownAndMissingEnvironment_areNonProductionAndRequireBothDestinations() {
    assertThatThrownBy(
            () ->
                config.registrationMessageGateway(
                    mock(MessageDeliveryPort.class),
                    mock(AuthWorkflowPolicyPort.class),
                    policy("future-env"),
                    properties(null, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mobile and email");
    assertThatThrownBy(
            () ->
                config.registrationMessageGateway(
                    mock(MessageDeliveryPort.class),
                    mock(AuthWorkflowPolicyPort.class),
                    new DeploymentEnvironmentPolicy(new MockEnvironment()),
                    properties(null, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mobile and email");
  }

  @Test
  void prodAndDr_doNotRequireOverrideConfiguration() {
    assertThat(
            config.registrationMessageGateway(
                mock(MessageDeliveryPort.class),
                mock(AuthWorkflowPolicyPort.class),
                policy("prod"),
                properties(null, null)))
        .isInstanceOf(RegistrationMessageDestinationOverrideAdapter.class);
    assertThat(
            config.registrationMessageGateway(
                mock(MessageDeliveryPort.class),
                mock(AuthWorkflowPolicyPort.class),
                policy("DR"),
                properties(null, null)))
        .isInstanceOf(RegistrationMessageDestinationOverrideAdapter.class);
  }

  private DeploymentEnvironmentPolicy policy(String profile) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    return new DeploymentEnvironmentPolicy(environment);
  }

  private AuthWorkflowProperties properties(String mobile, String email) {
    var properties = new AuthWorkflowProperties();
    var registration = new AuthWorkflowProperties.Registration();
    registration.getMessage().getDestinationOverride().setMobile(mobile);
    registration.getMessage().getDestinationOverride().setEmail(email);
    properties.setRegistration(registration);
    return properties;
  }
}
