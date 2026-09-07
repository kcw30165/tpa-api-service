package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccountGainLossApimProperties.class)
public class AccountGainLossApimValidationConfig {
  public AccountGainLossApimValidationConfig(AccountGainLossApimProperties properties) {
    properties.getPath();
  }
}
