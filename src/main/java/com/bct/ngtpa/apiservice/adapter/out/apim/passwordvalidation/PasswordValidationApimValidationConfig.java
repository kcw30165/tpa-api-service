package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordValidationApimProperties.class)
public class PasswordValidationApimValidationConfig {
  public PasswordValidationApimValidationConfig(PasswordValidationApimProperties properties) {
    properties.validate();
  }
}
