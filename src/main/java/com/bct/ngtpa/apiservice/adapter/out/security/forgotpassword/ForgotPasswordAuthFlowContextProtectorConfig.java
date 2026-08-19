package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword;

import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ForgotPasswordAuthFlowContextProperties.class)
public class ForgotPasswordAuthFlowContextProtectorConfig {
  @Bean
  @ConditionalOnMissingBean(ForgotPasswordAuthFlowContextProtectorPort.class)
  ForgotPasswordAuthFlowContextProtectorPort forgotPasswordAuthFlowContextProtector(
      ForgotPasswordAuthFlowContextProperties properties) {
    properties.validate();
    return new AesGcmForgotPasswordAuthFlowContextProtectorAdapter(
        properties.getKeyVersion().trim(), properties.getSecret());
  }
}
