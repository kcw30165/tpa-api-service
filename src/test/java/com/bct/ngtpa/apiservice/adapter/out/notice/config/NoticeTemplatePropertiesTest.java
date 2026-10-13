package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.EmailContent;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.NoticeDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoticeTemplatePropertiesTest {

  @Test
  void passesValidationWithSmsOnlyDefinition() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(Map.of("en", "Example OTP: {code}"));
    properties.setDefinitions(Map.of("registration-otp", definition));

    properties.validate();
  }

  @Test
  void passesValidationWithEmailOnlyDefinition() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    NoticeDefinition definition = new NoticeDefinition();
    EmailContent content = new EmailContent();
    content.setSubject("Example Subject");
    content.setBody("Example body {code}");
    definition.setEmail(Map.of("en", content));
    properties.setDefinitions(Map.of("username-recovery", definition));

    properties.validate();
  }

  @Test
  void rejectsDefinitionWithNeitherChannelConfigured() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    properties.setDefinitions(Map.of("login-otp", new NoticeDefinition()));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("login-otp")
        .hasMessageContaining("at least one channel");
  }

  @Test
  void rejectsNullDefinitionEntry() {
    Map<String, NoticeDefinition> definitions = new LinkedHashMap<>();
    definitions.put("login-otp", null);
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    properties.setDefinitions(definitions);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void rejectsBlankEmailSubject() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    NoticeDefinition definition = new NoticeDefinition();
    EmailContent content = new EmailContent();
    content.setSubject("   ");
    content.setBody("body");
    definition.setEmail(Map.of("en", content));
    properties.setDefinitions(Map.of("username-recovery", definition));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("subject and body");
  }

  @Test
  void rejectsBlankEmailBody() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    NoticeDefinition definition = new NoticeDefinition();
    EmailContent content = new EmailContent();
    content.setSubject("Subject");
    content.setBody("");
    definition.setEmail(Map.of("en", content));
    properties.setDefinitions(Map.of("username-recovery", definition));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("subject and body");
  }

  @Test
  void rejectsBlankSmsBody() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(Map.of("en", "   "));
    properties.setDefinitions(Map.of("registration-otp", definition));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void defaultsDefinitionsToEmptyMap() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();

    assertThat(properties.getDefinitions()).isEmpty();
    properties.validate();
  }
}
