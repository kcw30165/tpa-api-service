package com.bct.ngtpa.apiservice.config.metadata;

import com.bct.ngtpa.apiservice.application.usecase.metadata.GeneralMetadataResolverService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GeneralMetadataRuntimeConfig {
  @Bean
  GeneralMetadataResolverService generalMetadataResolverService(
      com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort cache,
      com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort source) {
    return new GeneralMetadataResolverService(cache, source);
  }
}
