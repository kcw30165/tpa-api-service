package com.bct.ngtpa.apiservice.adapter.out.security.audit;

import com.bct.ngtpa.apiservice.application.port.out.SecurityAuditEventPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityAuditEventAdapterConfig {

  @Bean
  @ConditionalOnMissingBean(SecurityAuditEventPort.class)
  SecurityAuditEventPort securityAuditEventPort() {
    return new LoggingSecurityAuditEventAdapter();
  }
}
