package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FundPriceDateFormatProperties.class)
public class FundPriceDateFormatValidationConfig {

  public FundPriceDateFormatValidationConfig(FundPriceDateFormatProperties properties) {
    properties.validate();
  }

  @Bean
  public java.time.format.DateTimeFormatter fundPriceFullDateFormatter(
      FundPriceDateFormatProperties properties) {
    return properties.fullDateFormatter();
  }
}
