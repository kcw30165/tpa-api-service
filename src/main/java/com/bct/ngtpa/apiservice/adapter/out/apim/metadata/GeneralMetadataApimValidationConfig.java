package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeneralMetadataApimProperties.class)
public class GeneralMetadataApimValidationConfig {
  public GeneralMetadataApimValidationConfig(GeneralMetadataApimProperties properties) {
    properties.validate();
  }
}
