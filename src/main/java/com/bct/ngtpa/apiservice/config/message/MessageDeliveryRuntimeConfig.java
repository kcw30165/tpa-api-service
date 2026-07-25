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
    @Bean
    @ConditionalOnMissingBean(MessageDeliveryPort.class)
    public MessageDeliveryPort messageDeliveryPort(
            SmsSenderPort smsSenderPort,
            EmailSenderPort emailSenderPort,
            MessageTemplateRenderer messageTemplateRenderer,
            DeploymentEnvironmentPolicy environmentPolicy,
            AuthWorkflowProperties properties) {
        MessageDeliveryPort delegate = new MessageDeliveryAdapter(
                smsSenderPort, emailSenderPort, messageTemplateRenderer);
        return command -> delegate.deliver(loginOverride(command, environmentPolicy, properties));
    }

    private MessageDeliveryCommand loginOverride(
            MessageDeliveryCommand command,
            DeploymentEnvironmentPolicy environmentPolicy,
            AuthWorkflowProperties properties) {
        if (command.scenario() != MessageDeliveryScenario.LOGIN
                || environmentPolicy.isProductionLikeDeployment()) {
            return command;
        }
        if (properties.getLogin() == null) {
            throw new IllegalStateException("auth-workflows.login configuration is required");
        }
        AuthWorkflowProperties.DestinationOverride override =
                properties.getLogin().getMessage().getDestinationOverride();
        override.validateRequiredDestinations();
        String recipient = command.channel() == MessageDeliveryChannel.EMAIL
                ? override.requiredEmail()
                : override.requiredMobile();
        return new MessageDeliveryCommand(
                command.messageType(), command.scenario(), command.channel(), recipient,
                command.subject(), command.body(), command.templateCode(), command.templateVariables(),
                command.locale(), command.correlationId());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplateRenderer messageTemplateRenderer() {
        return new DefaultMessageTemplateRenderer();
    }
}
