package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Fails startup when {@code apim.investment-choice.details.path} is blank. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvestmentChoiceDetailsApimProperties.class)
public class InvestmentChoiceDetailsApimValidationConfig {

  public InvestmentChoiceDetailsApimValidationConfig(
      InvestmentChoiceDetailsApimProperties properties) {
    properties.validate();
  }
}
