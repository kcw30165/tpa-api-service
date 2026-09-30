package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FundPriceModeApimProperties.class)
public class FundPriceModeApimValidationConfig {

  public FundPriceModeApimValidationConfig(FundPriceModeApimProperties properties) {
    properties.validate();
  }
}
