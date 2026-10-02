package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import com.bct.ngtpa.apiservice.adapter.out.notice.YamlNoticeTemplateRegistryAdapter;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the centralized Template Service, mirroring {@code
 * FundPriceDateFormatValidationConfig}'s exact established pattern in this codebase: plain {@code
 * @Configuration}, a constructor that validates the bound {@code @ConfigurationProperties} at
 * startup (fail-fast on misconfiguration), and a {@code @Bean} method returning what those
 * properties produce.
 *
 * <p>{@code application.yml} does not yet populate {@code notice-templates.definitions.*} with
 * final business-approved content (see
 * docs/design/10-template-service/template-service-solution-design.md section 8.3); {@link
 * NoticeTemplateProperties#validate()} only enforces structural correctness for whatever entries
 * are configured, so this wiring is safe to land ahead of content population.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NoticeTemplateProperties.class)
public class NoticeTemplateServiceConfig {

  public NoticeTemplateServiceConfig(NoticeTemplateProperties properties) {
    properties.validate();
  }

  @Bean
  @ConditionalOnMissingBean(NoticeTemplateRegistryPort.class)
  public NoticeTemplateRegistryPort noticeTemplateRegistryPort(NoticeTemplateProperties properties) {
    return new YamlNoticeTemplateRegistryAdapter(properties);
  }
}
