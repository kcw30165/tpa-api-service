package com.bct.ngtpa.apiservice.adapter.out.email.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.email.SmtpClient;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

class EmailSenderConfigTest {

  @Test
  void createsEmailSenderPortWhenEmailEnabled() {
    new ApplicationContextRunner()
        .withUserConfiguration(EmailSenderConfig.class)
        .withBean(SmtpClient.class, () -> command -> Mono.empty())
        .withBean(MaskingService.class, () -> new MaskingService(new MaskingProperties()))
        .withPropertyValues(
            "email.enabled=true",
            "email.smtp.host=smtp.example.test",
            "email.smtp.from=noreply@example.com")
        .run(context -> assertThat(context).hasSingleBean(EmailSenderPort.class));
  }

  @Test
  void doesNotCreateEmailSenderPortWhenEmailDisabled() {
    new ApplicationContextRunner()
        .withUserConfiguration(EmailSenderConfig.class)
        .withPropertyValues("email.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(EmailSenderPort.class));
  }
}
