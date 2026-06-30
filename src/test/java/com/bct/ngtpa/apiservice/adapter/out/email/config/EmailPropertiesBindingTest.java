package com.bct.ngtpa.apiservice.adapter.out.email.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class EmailPropertiesBindingTest {

    @Test
    void emailPropertiesBindDefaultsAndOverrides() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    EmailProperties properties = context.getBean(EmailProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getSmtp().getHost()).isEqualTo("");
                    assertThat(properties.getSmtp().getPort()).isEqualTo(25);
                    assertThat(properties.getSmtp().getFrom()).isEqualTo("");
                    assertThat(properties.getSmtp().getTimeoutMillis()).isEqualTo(5000);
                });

        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "email.enabled=false",
                        "email.smtp.host=smtp.example.test",
                        "email.smtp.port=2525",
                        "email.smtp.from=noreply@example.com",
                        "email.smtp.timeout-millis=9000")
                .run(context -> {
                    EmailProperties properties = context.getBean(EmailProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getSmtp().getHost()).isEqualTo("smtp.example.test");
                    assertThat(properties.getSmtp().getPort()).isEqualTo(2525);
                    assertThat(properties.getSmtp().getFrom()).isEqualTo("noreply@example.com");
                    assertThat(properties.getSmtp().getTimeoutMillis()).isEqualTo(9000);
                });
    }

    @Configuration
    @EnableConfigurationProperties(EmailProperties.class)
    static class TestConfig {
    }
}
