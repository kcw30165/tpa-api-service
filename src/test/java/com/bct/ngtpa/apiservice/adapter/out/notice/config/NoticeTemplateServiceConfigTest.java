package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Context-loading test for {@link NoticeTemplateServiceConfig}, using Spring Boot's standard {@link
 * ApplicationContextRunner} test support. This exists because missing/incorrect bean wiring is a
 * class of defect that pure unit tests (which construct classes manually) do not catch - see the
 * precedent noted in {@code FundPriceQueryExportUseCaseConfig}'s javadoc, which documents a real
 * Spring startup failure that only a context-loading or {@code spring-boot:run} smoke test could
 * have caught.
 */
class NoticeTemplateServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(NoticeTemplateServiceConfig.class);

  @Test
  void registersNoticeTemplateRegistryPortBeanWhenNoDefinitionsConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(NoticeTemplateRegistryPort.class);
        });
  }

  @Test
  void startsCleanlyWithAtLeastOneValidSmsOnlyDefinition() {
    contextRunner
        .withPropertyValues(
            "notice-templates.definitions.registration-otp.sms.en=Example OTP: {code}")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(NoticeTemplateRegistryPort.class);
            });
  }

  @Test
  void failsFastWhenAConfiguredDefinitionHasNeitherChannel() {
    contextRunner
        .withPropertyValues("notice-templates.definitions.login-otp.sms=")
        .run(context -> assertThat(context).hasFailed());
  }
}
