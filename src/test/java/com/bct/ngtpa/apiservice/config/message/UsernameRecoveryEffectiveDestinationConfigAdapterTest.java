package com.bct.ngtpa.apiservice.config.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationCommand;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import reactor.test.StepVerifier;

/**
 * Focused tests for the config-layer Username Recovery effective destination adapter: PROD/DR
 * identity, dedicated override usage for every other deployment, fail-closed missing overrides,
 * Login/Forgot Password override isolation, and no raw destination values in results or exceptions.
 */
class UsernameRecoveryEffectiveDestinationConfigAdapterTest {

  @ParameterizedTest
  @ValueSource(strings = {"PROD", "DR"})
  void productionLikeDeploymentReturnsVerifiedDestinationUnchanged(String profile) {
    var adapter = adapter(profile, properties());

    StepVerifier.create(adapter.resolve(emailCommand("member@example.test")))
        .assertNext(
            result -> {
              assertThat(result.destination()).isEqualTo("member@example.test");
              assertThat(result.channel())
                  .isEqualTo(UsernameRecoveryVerifiedDestination.Channel.EMAIL);
            })
        .verifyComplete();

    StepVerifier.create(adapter.resolve(mobileCommand("+852-member-mobile")))
        .assertNext(
            result -> {
              assertThat(result.destination()).isEqualTo("+852-member-mobile");
              assertThat(result.channel())
                  .isEqualTo(UsernameRecoveryVerifiedDestination.Channel.MOBILE);
            })
        .verifyComplete();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "local", "DEV", "SIT", "UAT", "QA", "unknown"})
  void everyNonProductionProfileUsesDedicatedOverrideForMatchingChannel(String profile) {
    var adapter = adapter(profile, properties());

    StepVerifier.create(adapter.resolve(emailCommand("member@example.test")))
        .assertNext(
            result -> {
              assertThat(result.destination()).isEqualTo("username-recovery@example.test");
              assertThat(result.channel())
                  .isEqualTo(UsernameRecoveryVerifiedDestination.Channel.EMAIL);
            })
        .verifyComplete();

    StepVerifier.create(adapter.resolve(mobileCommand("+852-member-mobile")))
        .assertNext(
            result -> {
              assertThat(result.destination()).isEqualTo("username-recovery-mobile");
              assertThat(result.channel())
                  .isEqualTo(UsernameRecoveryVerifiedDestination.Channel.MOBILE);
            })
        .verifyComplete();
  }

  @Test
  void loginAndForgotPasswordOverridesAreNeverUsed() {
    var properties = properties();
    properties.getLogin().getMessage().getDestinationOverride().setEmail("login@example.test");
    properties.getLogin().getMessage().getDestinationOverride().setMobile("login-mobile");
    properties
        .getForgotPassword()
        .getMessage()
        .getDestinationOverride()
        .setEmail("forgot@example.test");
    properties.getForgotPassword().getMessage().getDestinationOverride().setMobile("forgot-mobile");
    var adapter = adapter("DEV", properties);

    StepVerifier.create(adapter.resolve(emailCommand("member@example.test")))
        .assertNext(
            result -> assertThat(result.destination()).isEqualTo("username-recovery@example.test"))
        .verifyComplete();
    StepVerifier.create(adapter.resolve(mobileCommand("+852-member-mobile")))
        .assertNext(
            result -> assertThat(result.destination()).isEqualTo("username-recovery-mobile"))
        .verifyComplete();
  }

  @Test
  void missingEmailOverrideFailsClosedInNonProduction() {
    var properties = properties();
    properties.getUsernameRecovery().getMessage().getDestinationOverride().setEmail(null);
    var adapter = adapter("DEV", properties);

    StepVerifier.create(adapter.resolve(emailCommand("member@example.test")))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(IllegalStateException.class);
              assertThat(error.getMessage())
                  .contains("auth-workflows.username-recovery.message.destination-override")
                  .contains("email")
                  .doesNotContain("member@example.test");
            })
        .verify();
  }

  @Test
  void missingMobileOverrideFailsClosedInNonProduction() {
    var properties = properties();
    properties.getUsernameRecovery().getMessage().getDestinationOverride().setMobile(null);
    var adapter = adapter("DEV", properties);

    StepVerifier.create(adapter.resolve(mobileCommand("+852-member-mobile")))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(IllegalStateException.class);
              assertThat(error.getMessage())
                  .contains("auth-workflows.username-recovery.message.destination-override")
                  .contains("mobile")
                  .doesNotContain("+852-member-mobile");
            })
        .verify();
  }

  @Test
  void missingUsernameRecoveryConfigurationFailsClosedInNonProduction() {
    var adapter = adapter("DEV", new AuthWorkflowProperties());

    StepVerifier.create(adapter.resolve(emailCommand("member@example.test")))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(IllegalStateException.class);
              assertThat(error.getMessage())
                  .contains("auth-workflows.username-recovery")
                  .doesNotContain("member@example.test");
            })
        .verify();
  }

  @Test
  void resultToStringNeverDisclosesEffectiveDestination() {
    var adapter = adapter("DEV", properties());

    StepVerifier.create(adapter.resolve(mobileCommand("+852-member-mobile")))
        .assertNext(
            result -> {
              assertThat(result.toString())
                  .contains("<redacted>")
                  .doesNotContain("+852-member-mobile")
                  .doesNotContain("username-recovery-mobile");
            })
        .verifyComplete();
  }

  private UsernameRecoveryEffectiveDestinationCommand emailCommand(String destination) {
    return new UsernameRecoveryEffectiveDestinationCommand(
        new UsernameRecoveryVerifiedDestination(
            destination, UsernameRecoveryVerifiedDestination.Channel.EMAIL));
  }

  private UsernameRecoveryEffectiveDestinationCommand mobileCommand(String destination) {
    return new UsernameRecoveryEffectiveDestinationCommand(
        new UsernameRecoveryVerifiedDestination(
            destination, UsernameRecoveryVerifiedDestination.Channel.MOBILE));
  }

  private UsernameRecoveryEffectiveDestinationConfigAdapter adapter(
      String profile, AuthWorkflowProperties properties) {
    var environment = new MockEnvironment();
    if (!profile.isEmpty()) {
      environment.setActiveProfiles(profile);
    }
    return new UsernameRecoveryEffectiveDestinationConfigAdapter(
        new DeploymentEnvironmentPolicy(environment), properties);
  }

  private AuthWorkflowProperties properties() {
    var properties = new AuthWorkflowProperties();
    var login = new AuthWorkflowProperties.Login();
    login.getMessage().getDestinationOverride().setMobile("login-mobile");
    login.getMessage().getDestinationOverride().setEmail("login@example.test");
    properties.setLogin(login);
    var forgotPassword = new AuthWorkflowProperties.ForgotPassword();
    forgotPassword.getMessage().getDestinationOverride().setMobile("forgot-mobile");
    forgotPassword.getMessage().getDestinationOverride().setEmail("forgot@example.test");
    properties.setForgotPassword(forgotPassword);
    var usernameRecovery = new AuthWorkflowProperties.UsernameRecovery();
    usernameRecovery.getMessage().getDestinationOverride().setMobile("username-recovery-mobile");
    usernameRecovery
        .getMessage()
        .getDestinationOverride()
        .setEmail("username-recovery@example.test");
    properties.setUsernameRecovery(usernameRecovery);
    return properties;
  }
}
