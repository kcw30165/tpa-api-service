package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Fails startup when {@code apim.investment-choice.instruction.path} is blank. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvestmentChoiceInstructionApimProperties.class)
public class InvestmentChoiceInstructionApimValidationConfig {

  public InvestmentChoiceInstructionApimValidationConfig(
      InvestmentChoiceInstructionApimProperties properties) {
    properties.validate();
  }
}
