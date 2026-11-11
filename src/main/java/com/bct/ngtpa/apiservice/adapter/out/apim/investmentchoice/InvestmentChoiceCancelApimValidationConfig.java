package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Fails startup when {@code apim.investment-choice.cancel.path} is blank. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvestmentChoiceCancelApimProperties.class)
public class InvestmentChoiceCancelApimValidationConfig {

  public InvestmentChoiceCancelApimValidationConfig(
      InvestmentChoiceCancelApimProperties properties) {
    properties.validate();
  }
}
