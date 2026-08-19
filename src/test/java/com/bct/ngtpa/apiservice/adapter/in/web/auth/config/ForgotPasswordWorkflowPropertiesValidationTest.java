package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ForgotPasswordWorkflowPropertiesValidationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void missingIdentityMappingsFailStartupValidation() {
    contextRunner
        .withPropertyValues(
            "auth-workflows.forgot-password.username.min-length=4",
            "auth-workflows.forgot-password.username.max-length=128",
            "auth-workflows.forgot-password.username.trim=true",
            "auth-workflows.forgot-password.username.case-insensitive=true",
            "auth-workflows.forgot-password.allowed-public-identity-types[0]=ID",
            "auth-workflows.forgot-password.otp.length=6",
            "auth-workflows.forgot-password.otp.numeric-only=true",
            "auth-workflows.forgot-password.otp.ttl-seconds=300",
            "auth-workflows.forgot-password.otp.resend-cooldown-seconds=60",
            "auth-workflows.forgot-password.otp.max-verify-attempts=5",
            "auth-workflows.forgot-password.otp.resend-limit-enabled=true",
            "auth-workflows.forgot-password.otp.max-resend-attempts=5",
            "auth-workflows.forgot-password.otp.invalidate-previous-on-resend=true",
            "auth-workflows.forgot-password.otp.reset-verify-attempts-on-resend=true",
            "auth-workflows.forgot-password.otp.sms-template=Your code is {code}. It is valid for {ttlValue} {ttlUnit}.",
            "auth-workflows.forgot-password.otp.email-template.subject=Forgot password code",
            "auth-workflows.forgot-password.otp.email-template.body=Your code is {code}. It is valid for {ttlValue} {ttlUnit}.",
            "auth-workflows.forgot-password.idempotency.start-ttl-seconds=300",
            "auth-workflows.forgot-password.idempotency.details-and-otp-ttl-seconds=300",
            "auth-workflows.forgot-password.idempotency.otp-resend-ttl-seconds=300",
            "auth-workflows.forgot-password.idempotency.otp-verify-ttl-seconds=300",
            "auth-workflows.forgot-password.idempotency.complete-ttl-seconds=300",
            "auth-workflows.forgot-password.idempotency.cancel-ttl-seconds=300",
            "auth-workflows.forgot-password.reset-authorization.ttl-seconds=300")
        .run(
            context -> {
              assertThat(context.getStartupFailure()).isNotNull();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining(
                      "auth-workflows.forgot-password.tas-to-public-identity-type-mapping");
            });
  }

  // @Test
  // void missingTemplatePlaceholdersFailStartupValidation() {
  //     contextRunner
  //             .withPropertyValues(
  //                     "auth-workflows.forgot-password.username.min-length=4",
  //                     "auth-workflows.forgot-password.username.max-length=128",
  //                     "auth-workflows.forgot-password.username.trim=true",
  //                     "auth-workflows.forgot-password.username.case-insensitive=true",
  //                     "auth-workflows.forgot-password.allowed-public-identity-types[0]=ID",
  //                     "auth-workflows.forgot-password.tas-to-public-identity-type-mapping.ID=ID",
  //                     "auth-workflows.forgot-password.otp.length=6",
  //                     "auth-workflows.forgot-password.otp.numeric-only=true",
  //                     "auth-workflows.forgot-password.otp.ttl-seconds=300",
  //                     "auth-workflows.forgot-password.otp.resend-cooldown-seconds=60",
  //                     "auth-workflows.forgot-password.otp.max-verify-attempts=5",
  //                     "auth-workflows.forgot-password.otp.resend-limit-enabled=true",
  //                     "auth-workflows.forgot-password.otp.max-resend-attempts=5",
  //                     "auth-workflows.forgot-password.otp.invalidate-previous-on-resend=true",
  //                     "auth-workflows.forgot-password.otp.reset-verify-attempts-on-resend=true",
  //                     "auth-workflows.forgot-password.otp.localized-template.en.sms=Your code is
  // {code}. Valid for {ttlValue} {ttlUnit}",
  //
  // "auth-workflows.forgot-password.otp.localized-template.en.email-subject=Forgot password code",
  //                     "auth-workflows.forgot-password.otp.localized-template.en.email-body=Your
  // code is {code}.",
  //                     "auth-workflows.forgot-password.idempotency.start-ttl-seconds=300",
  //
  // "auth-workflows.forgot-password.idempotency.details-and-otp-ttl-seconds=300",
  //                     "auth-workflows.forgot-password.idempotency.otp-resend-ttl-seconds=300",
  //                     "auth-workflows.forgot-password.idempotency.otp-verify-ttl-seconds=300",
  //                     "auth-workflows.forgot-password.idempotency.complete-ttl-seconds=300",
  //                     "auth-workflows.forgot-password.idempotency.cancel-ttl-seconds=300",
  //                     "auth-workflows.forgot-password.reset-authorization.ttl-seconds=300")
  //             .run(context -> {
  //                 assertThat(context.getStartupFailure()).isNotNull();
  //                 assertThat(context.getStartupFailure())
  //
  // .hasMessageContaining("auth-workflows.forgot-password.otp.localized-template.en.sms");
  //             });
  // }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AuthWorkflowProperties.class)
  static class TestConfig {
    @Bean
    String forgotPasswordValidation(AuthWorkflowProperties properties) {
      properties.validateForgotPasswordPolicy();
      return "ok";
    }
  }
}
