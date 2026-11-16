package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VestingScaleApimProperties.class)
public class VestingScaleApimValidationConfig {

  public VestingScaleApimValidationConfig(VestingScaleApimProperties properties) {
    properties.validate();
  }
}
