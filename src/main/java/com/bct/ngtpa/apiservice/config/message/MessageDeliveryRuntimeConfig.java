package com.bct.ngtpa.apiservice.config.message;

import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.application.service.message.DefaultMessageTemplateRenderer;
import com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderer;
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
            MessageTemplateRenderer messageTemplateRenderer) {
        return new MessageDeliveryAdapter(
                smsSenderPort,
                emailSenderPort,
                messageTemplateRenderer);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplateRenderer messageTemplateRenderer() {
        return new DefaultMessageTemplateRenderer();
    }

}