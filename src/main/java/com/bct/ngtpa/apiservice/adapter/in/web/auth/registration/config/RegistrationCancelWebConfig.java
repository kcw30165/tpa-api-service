package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationCancelWebConfig {
  @Bean
  @ConditionalOnMissingBean
  RegistrationCancellationPageMapper registrationCancellationPageMapper(BffPagesProperties pages) {
    return new RegistrationCancellationPageMapper(pages);
  }

  @Bean
  @ConditionalOnMissingBean
  CancelRegistrationWebMapper cancelRegistrationWebMapper(
      RegistrationCancellationPageMapper pageMapper) {
    return new CancelRegistrationWebMapper(pageMapper);
  }
}
