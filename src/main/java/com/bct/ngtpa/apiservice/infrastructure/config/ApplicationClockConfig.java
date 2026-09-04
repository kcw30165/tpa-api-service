package com.bct.ngtpa.apiservice.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationClockConfig {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock applicationClock() {
    return Clock.systemUTC();
  }
}
