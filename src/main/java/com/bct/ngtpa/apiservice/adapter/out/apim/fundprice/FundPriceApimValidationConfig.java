package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Mirrors {@link FundPriceModeApimValidationConfig}'s exact shape for the price operation. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FundPriceApimProperties.class)
public class FundPriceApimValidationConfig {

  public FundPriceApimValidationConfig(FundPriceApimProperties properties) {
    properties.validate();
  }
}
