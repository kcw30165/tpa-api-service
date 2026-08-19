package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RegistrationIdempotencyPropertiesBindingTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void bindsRegistrationIdempotencyOverridesFromAuthWorkflowRegistrationPolicyRoot() {
    runner
        .withPropertyValues(
            "auth-workflows.registration.idempotency.start-ttl-seconds=111",
            "auth-workflows.registration.idempotency.otp-resend-ttl-seconds=222",
            "auth-workflows.registration.idempotency.otp-verify-ttl-seconds=333",
            "auth-workflows.registration.idempotency.complete-ttl-seconds=444")
        .run(
            context -> {
              var properties = context.getBean(RegistrationIdempotencyProperties.class);

              assertThat(properties.startTtl().getSeconds()).isEqualTo(111);
              assertThat(properties.otpResendTtl().getSeconds()).isEqualTo(222);
              assertThat(properties.otpVerifyTtl().getSeconds()).isEqualTo(333);
              assertThat(properties.completeTtl().getSeconds()).isEqualTo(444);
            });
  }

  @Configuration
  @EnableConfigurationProperties(RegistrationIdempotencyProperties.class)
  static class TestConfig {}
}
