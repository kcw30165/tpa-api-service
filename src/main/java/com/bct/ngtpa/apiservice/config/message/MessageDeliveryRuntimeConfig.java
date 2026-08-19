package com.bct.ngtpa.apiservice.config.message;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import com.bct.ngtpa.apiservice.application.service.message.DefaultMessageTemplateRenderer;
import com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderer;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageDeliveryRuntimeConfig {
  private static final String LOGIN_OVERRIDE_PREFIX =
      "auth-workflows.login.message.destination-override";
  private static final String FORGOT_PASSWORD_OVERRIDE_PREFIX =
      "auth-workflows.forgot-password.message.destination-override";
  private static final String USERNAME_RECOVERY_OVERRIDE_PREFIX =
      "auth-workflows.username-recovery.message.destination-override";

  @Bean
  @ConditionalOnMissingBean(MessageDeliveryPort.class)
  public MessageDeliveryPort messageDeliveryPort(
      SmsSenderPort smsSenderPort,
      EmailSenderPort emailSenderPort,
      MessageTemplateRenderer messageTemplateRenderer,
      DeploymentEnvironmentPolicy environmentPolicy,
      AuthWorkflowProperties properties) {
    MessageDeliveryPort delegate =
        new MessageDeliveryAdapter(smsSenderPort, emailSenderPort, messageTemplateRenderer);
    return command -> delegate.deliver(scenarioOverride(command, environmentPolicy, properties));
  }

  private MessageDeliveryCommand scenarioOverride(
      MessageDeliveryCommand command,
      DeploymentEnvironmentPolicy environmentPolicy,
      AuthWorkflowProperties properties) {
    if (environmentPolicy.isProductionLikeDeployment()) {
      return command;
    }
    AuthWorkflowProperties.DestinationOverride override =
        switch (command.scenario()) {
          case LOGIN -> requiredLoginOverride(properties);
          case FORGOT_PASSWORD -> requiredForgotPasswordOverride(properties);
          case USERNAME_RECOVERY -> requiredUsernameRecoveryOverride(properties);
          default -> null;
        };
    if (override == null) {
      return command;
    }
    String recipient =
        command.channel() == MessageDeliveryChannel.EMAIL
            ? override.requiredEmail(prefix(command.scenario()))
            : override.requiredMobile(prefix(command.scenario()));
    return new MessageDeliveryCommand(
        command.messageType(),
        command.scenario(),
        command.channel(),
        recipient,
        command.subject(),
        command.body(),
        command.templateCode(),
        command.templateVariables(),
        command.locale(),
        command.correlationId());
  }

  private AuthWorkflowProperties.DestinationOverride requiredLoginOverride(
      AuthWorkflowProperties properties) {
    if (properties.getLogin() == null) {
      throw new IllegalStateException("auth-workflows.login configuration is required");
    }
    AuthWorkflowProperties.DestinationOverride override =
        properties.getLogin().getMessage().getDestinationOverride();
    override.validateRequiredDestinations(LOGIN_OVERRIDE_PREFIX);
    return override;
  }

  private AuthWorkflowProperties.DestinationOverride requiredForgotPasswordOverride(
      AuthWorkflowProperties properties) {
    if (properties.getForgotPassword() == null) {
      throw new IllegalStateException("auth-workflows.forgot-password configuration is required");
    }
    AuthWorkflowProperties.DestinationOverride override =
        properties.getForgotPassword().getMessage().getDestinationOverride();
    override.validateRequiredDestinations(FORGOT_PASSWORD_OVERRIDE_PREFIX);
    return override;
  }

  private AuthWorkflowProperties.DestinationOverride requiredUsernameRecoveryOverride(
      AuthWorkflowProperties properties) {
    if (properties.getUsernameRecovery() == null) {
      throw new IllegalStateException("auth-workflows.username-recovery configuration is required");
    }
    AuthWorkflowProperties.DestinationOverride override =
        properties.getUsernameRecovery().getMessage().getDestinationOverride();
    override.validateRequiredDestinations(USERNAME_RECOVERY_OVERRIDE_PREFIX);
    return override;
  }

  private String prefix(MessageDeliveryScenario scenario) {
    return switch (scenario) {
      case LOGIN -> LOGIN_OVERRIDE_PREFIX;
      case FORGOT_PASSWORD -> FORGOT_PASSWORD_OVERRIDE_PREFIX;
      case USERNAME_RECOVERY -> USERNAME_RECOVERY_OVERRIDE_PREFIX;
      default -> LOGIN_OVERRIDE_PREFIX;
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public MessageTemplateRenderer messageTemplateRenderer() {
    return new DefaultMessageTemplateRenderer();
  }
}
