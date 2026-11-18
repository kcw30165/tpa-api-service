package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DownloadCornerApimProperties.class)
public class DownloadCornerApimValidationConfig {

  public DownloadCornerApimValidationConfig(DownloadCornerApimProperties properties) {
    properties.validate();
  }
}
