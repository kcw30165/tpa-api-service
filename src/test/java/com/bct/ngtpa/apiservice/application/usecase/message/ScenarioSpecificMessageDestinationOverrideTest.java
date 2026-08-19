package com.bct.ngtpa.apiservice.application.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.config.message.MessageDeliveryRuntimeConfig;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ScenarioSpecificMessageDestinationOverrideTest {
  @Test
  void
      nonProductionLoginAndForgotPasswordUseScenarioOverrideWhileRegistrationRemainsUnchangedAtGenericBoundary() {
    var properties = properties();
    var sms = new AtomicReference<String>();
    var email = new AtomicReference<String>();
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> {
                  sms.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> {
                  email.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null));
                },
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(command.template()),
                new DeploymentEnvironmentPolicy(environment("DEV")),
                properties);
    StepVerifier.create(
            port.deliver(
                command(MessageDeliveryScenario.LOGIN, MessageDeliveryChannel.EMAIL, "real-login")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(email.get()).isEqualTo("login@example.test");
    StepVerifier.create(
            port.deliver(
                command(
                    MessageDeliveryScenario.FORGOT_PASSWORD,
                    MessageDeliveryChannel.SMS,
                    "real-forgot")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(sms.get()).isEqualTo("forgot-mobile");
    StepVerifier.create(
            port.deliver(
                command(
                    MessageDeliveryScenario.REGISTRATION,
                    MessageDeliveryChannel.SMS,
                    "real-registration")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(sms.get()).isEqualTo("real-registration");
  }

  @Test
  void productionLoginUsesOriginalRecipient() {
    var sms = new AtomicReference<String>();
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> {
                  sms.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null)),
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(command.template()),
                new DeploymentEnvironmentPolicy(environment("PROD")),
                properties());
    StepVerifier.create(
            port.deliver(
                command(MessageDeliveryScenario.LOGIN, MessageDeliveryChannel.SMS, "real-login")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(sms.get()).isEqualTo("real-login");
  }

  @Test
  void
      nonProductionUsernameRecoveryUsesDedicatedOverrideAndNeverReusesLoginOrForgotPasswordOverrides() {
    var properties = properties();
    var sms = new AtomicReference<String>();
    var email = new AtomicReference<String>();
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> {
                  sms.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> {
                  email.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null));
                },
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(command.template()),
                new DeploymentEnvironmentPolicy(environment("DEV")),
                properties);
    StepVerifier.create(
            port.deliver(
                command(
                    MessageDeliveryScenario.USERNAME_RECOVERY,
                    MessageDeliveryChannel.EMAIL,
                    "real-username-recovery")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(email.get()).isEqualTo("username-recovery@example.test");
    assertThat(email.get()).isNotEqualTo("login@example.test");
    assertThat(email.get()).isNotEqualTo("forgot@example.test");
    StepVerifier.create(
            port.deliver(
                command(
                    MessageDeliveryScenario.USERNAME_RECOVERY,
                    MessageDeliveryChannel.SMS,
                    "real-username-recovery-mobile")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(sms.get()).isEqualTo("username-recovery-mobile");
    assertThat(sms.get()).isNotEqualTo("login-mobile");
    assertThat(sms.get()).isNotEqualTo("forgot-mobile");
  }

  @Test
  void productionLikeProfilesUseVerifiedMemberDestinationForUsernameRecovery() {
    for (String profile : java.util.List.of("PROD", "DR")) {
      var sms = new AtomicReference<String>();
      MessageDeliveryPort port =
          new MessageDeliveryRuntimeConfig()
              .messageDeliveryPort(
                  message -> {
                    sms.set(message.recipient());
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                  },
                  message ->
                      Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null)),
                  command ->
                      new com.bct.ngtpa.apiservice.application.service.message
                          .MessageTemplateRenderResult(command.template()),
                  new DeploymentEnvironmentPolicy(environment(profile)),
                  properties());
      StepVerifier.create(
              port.deliver(
                  command(
                      MessageDeliveryScenario.USERNAME_RECOVERY,
                      MessageDeliveryChannel.SMS,
                      "real-member-mobile")))
          .expectNextCount(1)
          .verifyComplete();
      assertThat(sms.get()).isEqualTo("real-member-mobile");
    }
  }

  @Test
  void usernameRecoveryOverridePreservesTemplateVariablesUsernameContentAndCorrelation() {
    var properties = properties();
    var delivered = new AtomicReference<EmailMessage>();
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null)),
                message -> {
                  delivered.set(message);
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null));
                },
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(
                        command
                            .template()
                            .replace(
                                "{username}",
                                command.templateVariables().getOrDefault("username", ""))),
                new DeploymentEnvironmentPolicy(environment("UAT")),
                properties);
    MessageDeliveryCommand command =
        new MessageDeliveryCommand(
            MessageDeliveryType.USERNAME_RECOVERY,
            MessageDeliveryScenario.USERNAME_RECOVERY,
            MessageDeliveryChannel.EMAIL,
            "real-member@example.test",
            "Your BCT username",
            "Your username is {username}.",
            "template-code-1",
            Map.of("username", "member-username"),
            "zh_HK",
            "corr-1");
    StepVerifier.create(port.deliver(command))
        .expectNextMatches(result -> result.status() == MessageDeliveryStatus.ACCEPTED)
        .verifyComplete();

    assertThat(delivered.get()).isNotNull();
    assertThat(delivered.get().recipient()).isEqualTo("username-recovery@example.test");
    assertThat(delivered.get().subject()).isEqualTo("Your BCT username");
    assertThat(delivered.get().body()).isEqualTo("Your username is member-username.");
    assertThat(delivered.get().correlationId()).isEqualTo("corr-1");
  }

  @Test
  void missingNonProductionEmailOverrideFailsClosedForUsernameRecovery() {
    var properties = propertiesWithoutUsernameRecoveryEmail();
    var email = new AtomicReference<String>();
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null)),
                message -> {
                  email.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null));
                },
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(command.template()),
                new DeploymentEnvironmentPolicy(environment("DEV")),
                properties);
    assertThatThrownBy(
            () ->
                port.deliver(
                    command(
                        MessageDeliveryScenario.USERNAME_RECOVERY,
                        MessageDeliveryChannel.EMAIL,
                        "real-member@example.test")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth-workflows.username-recovery.message.destination-override")
        .hasMessageContaining("email");
    assertThat(email.get()).isNull();
  }

  @Test
  void missingNonProductionMobileOverrideFailsClosedForUsernameRecovery() {
    var properties = propertiesWithoutUsernameRecoveryMobile();
    var sms = new AtomicReference<String>();
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> {
                  sms.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null)),
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(command.template()),
                new DeploymentEnvironmentPolicy(environment("DEV")),
                properties);
    assertThatThrownBy(
            () ->
                port.deliver(
                    command(
                        MessageDeliveryScenario.USERNAME_RECOVERY,
                        MessageDeliveryChannel.SMS,
                        "real-member-mobile")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth-workflows.username-recovery.message.destination-override")
        .hasMessageContaining("mobile");
    assertThat(sms.get()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "local", "DEV", "SIT", "UAT", "QA", "unknown"})
  void everyNonProductionProfileUsesDedicatedOverrideForUsernameRecovery(String profile) {
    var properties = properties();
    var sms = new AtomicReference<String>();
    var environment = new MockEnvironment();
    if (!profile.isEmpty()) {
      environment.setActiveProfiles(profile);
    }
    MessageDeliveryPort port =
        new MessageDeliveryRuntimeConfig()
            .messageDeliveryPort(
                message -> {
                  sms.set(message.recipient());
                  return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null)),
                command ->
                    new com.bct.ngtpa.apiservice.application.service.message
                        .MessageTemplateRenderResult(command.template()),
                new DeploymentEnvironmentPolicy(environment),
                properties);
    StepVerifier.create(
            port.deliver(
                command(
                    MessageDeliveryScenario.USERNAME_RECOVERY,
                    MessageDeliveryChannel.SMS,
                    "real-member-mobile")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(sms.get()).isEqualTo("username-recovery-mobile");
  }

  private AuthWorkflowProperties propertiesWithoutUsernameRecoveryMobile() {
    var properties = properties();
    properties.getUsernameRecovery().getMessage().getDestinationOverride().setMobile(null);
    return properties;
  }

  private AuthWorkflowProperties propertiesWithoutUsernameRecoveryEmail() {
    var properties = properties();
    properties.getUsernameRecovery().getMessage().getDestinationOverride().setEmail(null);
    return properties;
  }

  private MockEnvironment environment(String profile) {
    var e = new MockEnvironment();
    e.setActiveProfiles(profile);
    return e;
  }

  private AuthWorkflowProperties properties() {
    var p = new AuthWorkflowProperties();
    var r = new AuthWorkflowProperties.Registration();
    r.getMessage().getDestinationOverride().setMobile("reg-mobile");
    r.getMessage().getDestinationOverride().setEmail("reg@example.test");
    p.setRegistration(r);
    var l = new AuthWorkflowProperties.Login();
    l.getMessage().getDestinationOverride().setMobile("login-mobile");
    l.getMessage().getDestinationOverride().setEmail("login@example.test");
    p.setLogin(l);
    var f = new AuthWorkflowProperties.ForgotPassword();
    f.getMessage().getDestinationOverride().setMobile("forgot-mobile");
    f.getMessage().getDestinationOverride().setEmail("forgot@example.test");
    p.setForgotPassword(f);
    var u = new AuthWorkflowProperties.UsernameRecovery();
    u.getMessage().getDestinationOverride().setMobile("username-recovery-mobile");
    u.getMessage().getDestinationOverride().setEmail("username-recovery@example.test");
    p.setUsernameRecovery(u);
    return p;
  }

  private MessageDeliveryCommand command(
      MessageDeliveryScenario scenario, MessageDeliveryChannel channel, String recipient) {
    return new MessageDeliveryCommand(
        MessageDeliveryType.LOGIN_OTP,
        scenario,
        channel,
        recipient,
        channel == MessageDeliveryChannel.EMAIL ? "subject" : null,
        "code {code}",
        null,
        Map.of("code", "123456"),
        "en",
        "req-1");
  }
}
