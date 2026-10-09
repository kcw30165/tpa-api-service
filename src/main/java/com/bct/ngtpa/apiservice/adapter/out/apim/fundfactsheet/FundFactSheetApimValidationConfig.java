package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Fails startup when {@code apim.fund-fact-sheet.path} is blank. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FundFactSheetApimProperties.class)
public class FundFactSheetApimValidationConfig {

  public FundFactSheetApimValidationConfig(FundFactSheetApimProperties properties) {
    properties.validate();
  }
}
