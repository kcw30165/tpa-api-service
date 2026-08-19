package com.bct.ngtpa.apiservice.adapter.out.security.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.SecurityAuditEventPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityAuditEventAdapterConfigTest {

  @Test
  void registersLoggingSecurityAuditEventAdapterAsSecurityAuditEventPortBean() {
    new ApplicationContextRunner()
        .withUserConfiguration(SecurityAuditEventAdapterConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecurityAuditEventPort.class);
              assertThat(context.getBean(SecurityAuditEventPort.class))
                  .isInstanceOf(LoggingSecurityAuditEventAdapter.class);
            });
  }

  @Test
  void allowsOverrideOfSecurityAuditEventPortBean() {
    SecurityAuditEventPort customPort = event -> reactor.core.publisher.Mono.empty();
    new ApplicationContextRunner()
        .withUserConfiguration(SecurityAuditEventAdapterConfig.class)
        .withBean(SecurityAuditEventPort.class, () -> customPort)
        .run(
            context ->
                assertThat(context.getBean(SecurityAuditEventPort.class)).isSameAs(customPort));
  }
}
