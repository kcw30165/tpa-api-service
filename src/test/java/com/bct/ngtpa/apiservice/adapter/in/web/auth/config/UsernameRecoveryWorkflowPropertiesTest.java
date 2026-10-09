package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Task 016 fail-first tests for the typed Username Recovery workflow configuration section.
 *
 * <p>Verifies the approved default session TTL (600 seconds), the dedicated message configuration
 * built on the established {@link AuthWorkflowProperties.Message} and {@link
 * AuthWorkflowProperties.DestinationOverride} types, and positive validation of the session TTL.
 * Error messages disclose property names only, never configured values.
 */
class UsernameRecoveryWorkflowPropertiesTest {

  @Test
  void usernameRecoverySectionIsTypedWithApprovedDefaultSessionTtlAndDedicatedMessageConfig() {
    var usernameRecovery = new AuthWorkflowProperties.UsernameRecovery();

    assertThat(usernameRecovery.getSession()).isNotNull();
    assertThat(usernameRecovery.getSession().getTtlSeconds()).isEqualTo(600);
    assertThat(usernameRecovery.getMessage()).isNotNull();
    assertThat(usernameRecovery.getMessage().getDestinationOverride()).isNotNull();

    var properties = new AuthWorkflowProperties();
    properties.setUsernameRecovery(usernameRecovery);
    assertThat(properties.getUsernameRecovery()).isSameAs(usernameRecovery);
  }

  @Test
  void validateUsernameRecoveryPolicyAcceptsPositiveSessionTtl() {
    var usernameRecovery = new AuthWorkflowProperties.UsernameRecovery();
    usernameRecovery.getSession().setTtlSeconds(600);

    var properties = new AuthWorkflowProperties();
    properties.setUsernameRecovery(usernameRecovery);

    assertThatCode(properties::validateUsernameRecoveryPolicy).doesNotThrowAnyException();
  }

  @Test
  void validateUsernameRecoveryPolicyRejectsNonPositiveSessionTtl() {
    var usernameRecovery = new AuthWorkflowProperties.UsernameRecovery();
    usernameRecovery.getSession().setTtlSeconds(0);

    var properties = new AuthWorkflowProperties();
    properties.setUsernameRecovery(usernameRecovery);

    assertThatThrownBy(properties::validateUsernameRecoveryPolicy)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth-workflows.username-recovery.session.ttl-seconds")
        .hasMessageContaining("greater than zero");
  }

  @Test
  void validateUsernameRecoveryPolicyRejectsMissingSection() {
    var properties = new AuthWorkflowProperties();

    assertThatThrownBy(properties::validateUsernameRecoveryPolicy)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth-workflows.username-recovery configuration is required");
  }

  @Test
  void validateUsernameRecoveryPolicyErrorDisclosesPropertyNamesOnly() {
    var usernameRecovery = new AuthWorkflowProperties.UsernameRecovery();
    usernameRecovery.getMessage().getDestinationOverride().setEmail("member-test@example.test");
    usernameRecovery.getSession().setTtlSeconds(-1);

    var properties = new AuthWorkflowProperties();
    properties.setUsernameRecovery(usernameRecovery);

    assertThatThrownBy(properties::validateUsernameRecoveryPolicy)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth-workflows.username-recovery.session.ttl-seconds")
        .hasMessageNotContaining("member-test@example.test");
  }
}
