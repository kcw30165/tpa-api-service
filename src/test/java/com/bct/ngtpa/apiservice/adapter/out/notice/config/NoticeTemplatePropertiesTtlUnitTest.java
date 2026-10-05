package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.EmailContent;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.NoticeDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Validation of {@code notice-templates.ttl-units} (solution design section 8.6). */
class NoticeTemplatePropertiesTtlUnitTest {

  private static final String WITH_UNIT_EN = "Valid for {ttlValue} {ttlUnit}.";
  private static final String WITH_UNIT_ZH = "有效期為{ttlValue}{ttlUnit}。";
  private static final String WITHOUT_UNIT = "Your code is {code}.";

  @Test
  void defaultsTtlUnitsToAnEmptyMap() {
    assertThat(new NoticeTemplateProperties().getTtlUnits()).isEmpty();
  }

  @Test
  void rejectsSmsContentThatUsesTheUnitWhenNoUnitIsConfiguredForItsLocale() {
    NoticeTemplateProperties properties = smsOnly(Map.of("en", WITH_UNIT_EN));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units.en")
        .hasMessageContaining("registration-otp")
        .hasMessageContaining("sms");
  }

  @Test
  void rejectsAnEmailSubjectThatUsesTheUnitWhenNoUnitIsConfigured() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setEmail(Map.of("en", email(WITH_UNIT_EN, WITHOUT_UNIT)));
    NoticeTemplateProperties properties = withDefinition(definition);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units.en")
        .hasMessageContaining("email");
  }

  @Test
  void rejectsAnEmailBodyThatUsesTheUnitWhenNoUnitIsConfigured() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setEmail(Map.of("zh_HK", email("Subject", WITH_UNIT_ZH)));
    NoticeTemplateProperties properties = withDefinition(definition);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units.zh_HK")
        .hasMessageContaining("email");
  }

  @Test
  void rejectsABlankUnit() {
    NoticeTemplateProperties properties = smsOnly(Map.of("en", WITH_UNIT_EN));
    properties.setTtlUnits(Map.of("en", "   "));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units.en");
  }

  @Test
  void namesTheLocaleKeyOfTheContentNotAnotherLocale() {
    NoticeTemplateProperties properties =
        smsOnly(Map.of("en", WITH_UNIT_EN, "zh_HK", WITH_UNIT_ZH));
    properties.setTtlUnits(Map.of("en", "minutes"));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units.zh_HK");
  }

  @Test
  void acceptsACompleteConfiguration() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(Map.of("en", WITH_UNIT_EN, "zh_HK", WITH_UNIT_ZH));
    definition.setEmail(
        Map.of(
            "en", email("Subject", WITH_UNIT_EN),
            "zh_HK", email("主旨", WITH_UNIT_ZH)));
    NoticeTemplateProperties properties = withDefinition(definition);
    properties.setTtlUnits(Map.of("en", "minutes", "zh_HK", "分鐘"));

    properties.validate();
  }

  @Test
  void acceptsContentWithoutTheUnitPlaceholderWhenNoUnitIsConfigured() {
    NoticeTemplateProperties properties = smsOnly(Map.of("en", WITHOUT_UNIT));

    properties.validate();
  }

  @Test
  void treatsANullUnitsMapAsEmpty() {
    NoticeTemplateProperties without = smsOnly(Map.of("en", WITHOUT_UNIT));
    without.setTtlUnits(null);
    without.validate();

    NoticeTemplateProperties with = smsOnly(Map.of("en", WITH_UNIT_EN));
    with.setTtlUnits(null);
    assertThatThrownBy(with::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units.en");
  }

  @Test
  void acceptsDefinitionsThatConfigureOnlyOneChannel() {
    NoticeDefinition smsOnly = new NoticeDefinition();
    smsOnly.setSms(Map.of("en", WITH_UNIT_EN));
    smsOnly.setEmail(null);
    NoticeTemplateProperties smsProperties = withDefinition(smsOnly);
    smsProperties.setTtlUnits(Map.of("en", "minutes"));
    smsProperties.validate();

    NoticeDefinition emailOnly = new NoticeDefinition();
    emailOnly.setSms(null);
    emailOnly.setEmail(Map.of("en", email("Subject", WITH_UNIT_EN)));
    NoticeTemplateProperties emailProperties = withDefinition(emailOnly);
    emailProperties.setTtlUnits(Map.of("en", "minutes"));
    emailProperties.validate();
  }

  private static NoticeTemplateProperties smsOnly(Map<String, String> sms) {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(sms);
    return withDefinition(definition);
  }

  private static NoticeTemplateProperties withDefinition(NoticeDefinition definition) {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    properties.setDefinitions(Map.of("registration-otp", definition));
    return properties;
  }

  private static EmailContent email(String subject, String body) {
    EmailContent content = new EmailContent();
    content.setSubject(subject);
    content.setBody(body);
    return content;
  }
}
