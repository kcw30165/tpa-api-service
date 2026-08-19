package com.bct.ngtpa.apiservice.adapter.out.email.config;

import com.bct.ngtpa.apiservice.adapter.out.email.PlainSocketSmtpClient;
import com.bct.ngtpa.apiservice.adapter.out.email.SmtpClient;
import com.bct.ngtpa.apiservice.adapter.out.email.SmtpEmailSenderAdapter;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
@ConditionalOnProperty(name = "email.enabled", havingValue = "true", matchIfMissing = true)
public class EmailSenderConfig {

  @Bean
  @ConditionalOnMissingBean
  public SmtpClient smtpClient() {
    return new PlainSocketSmtpClient();
  }

  @Bean
  public EmailSenderPort emailSenderPort(SmtpClient smtpClient, EmailProperties emailProperties) {
    return new SmtpEmailSenderAdapter(smtpClient, emailProperties);
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound
      emailMessageDeliveryTimeoutBound(EmailProperties emailProperties) {
    return new com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound(
        com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel.EMAIL,
        emailProperties.getSmtp().getTimeoutMillis());
  }
}
