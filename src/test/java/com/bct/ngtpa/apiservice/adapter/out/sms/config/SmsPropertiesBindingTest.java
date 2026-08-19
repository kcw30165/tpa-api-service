package com.bct.ngtpa.apiservice.adapter.out.sms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SmsPropertiesBindingTest {

  @Test
  void smsPropertiesBindDefaultsAndOverrides() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              SmsProperties properties = context.getBean(SmsProperties.class);

              assertThat(properties.isEnabled()).isTrue();
              assertThat(properties.getSendSinglePath()).isEqualTo("/sms/ext/sendSingleSMS.do");
              assertThat(properties.getDefaultCountry()).isEqualTo("HK");
              assertThat(properties.getTimeoutMillis()).isEqualTo(5000);
            });

    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "sms.enabled=false",
            "sms.base-url=https://sms.example.test",
            "sms.send-single-path=/custom/send",
            "sms.username=sms-user",
            "sms.password=sms-password",
            "sms.send-id=BCT",
            "sms.class-code=OTP",
            "sms.default-country=MO",
            "sms.timeout-millis=9000")
        .run(
            context -> {
              SmsProperties properties = context.getBean(SmsProperties.class);

              assertThat(properties.isEnabled()).isFalse();
              assertThat(properties.getBaseUrl()).isEqualTo("https://sms.example.test");
              assertThat(properties.getSendSinglePath()).isEqualTo("/custom/send");
              assertThat(properties.getUsername()).isEqualTo("sms-user");
              assertThat(properties.getPassword()).isEqualTo("sms-password");
              assertThat(properties.getSendId()).isEqualTo("BCT");
              assertThat(properties.getClassCode()).isEqualTo("OTP");
              assertThat(properties.getDefaultCountry()).isEqualTo("MO");
              assertThat(properties.getTimeoutMillis()).isEqualTo(9000);
            });
  }

  @Configuration
  @EnableConfigurationProperties(SmsProperties.class)
  static class TestConfig {}
}
