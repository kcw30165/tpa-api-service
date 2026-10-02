package com.bct.ngtpa.apiservice.adapter.out.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.EmailContent;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.NoticeDefinition;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlNoticeTemplateRegistryAdapterTest {

  @Test
  void resolvesSmsTemplateForExactLocale() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(registrationOtpDefinition());

    NoticeTemplateDefinition result =
        adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.SMS, "en");

    assertThat(result.body()).isEqualTo("Example registration OTP: {code}");
    assertThat(result.subject()).isNull();
    assertThat(result.requiredPlaceholders()).containsExactly("code");
  }

  @Test
  void resolvesSmsTemplateForZhHkLocale() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(registrationOtpDefinition());

    NoticeTemplateDefinition result =
        adapter.resolve(
            MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.SMS, "zh_HK");

    assertThat(result.body()).isEqualTo("範例註冊一次性密碼：{code}");
  }

  @Test
  void fallsBackToEnglishWhenLocaleMissingForSms() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(registrationOtpDefinition());

    NoticeTemplateDefinition result =
        adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.SMS, "fr");

    assertThat(result.body()).isEqualTo("Example registration OTP: {code}");
  }

  @Test
  void resolvesEmailTemplateWithSubjectAndBody() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(usernameRecoveryDefinition());

    NoticeTemplateDefinition result =
        adapter.resolve(MessageDeliveryType.USERNAME_RECOVERY, MessageDeliveryChannel.EMAIL, "en");

    assertThat(result.subject()).isEqualTo("Example Username Recovery Subject");
    assertThat(result.body()).isEqualTo("Example body for {username} at {schemeName}");
    assertThat(result.requiredPlaceholders()).containsExactlyInAnyOrder("username", "schemeName");
  }

  @Test
  void defaultsNullLocaleToEnglish() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(registrationOtpDefinition());

    NoticeTemplateDefinition result =
        adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.SMS, null);

    assertThat(result.locale()).isEqualTo("en");
  }

  @Test
  void throwsWhenMessageTypeNotConfigured() {
    YamlNoticeTemplateRegistryAdapter adapter =
        new YamlNoticeTemplateRegistryAdapter(new NoticeTemplateProperties());

    assertThatThrownBy(
            () ->
                adapter.resolve(
                    MessageDeliveryType.LOGIN_OTP, MessageDeliveryChannel.SMS, "en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("login-otp");
  }

  @Test
  void throwsWhenEmailLocaleAndFallbackBothMissing() {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    NoticeDefinition definition = new NoticeDefinition();
    EmailContent frContent = new EmailContent();
    frContent.setSubject("Sujet");
    frContent.setBody("Corps");
    definition.setEmail(Map.of("fr", frContent));
    properties.setDefinitions(Map.of("username-recovery", definition));
    YamlNoticeTemplateRegistryAdapter adapter = new YamlNoticeTemplateRegistryAdapter(properties);

    assertThatThrownBy(
            () ->
                adapter.resolve(
                    MessageDeliveryType.USERNAME_RECOVERY, MessageDeliveryChannel.EMAIL, "de"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("username-recovery");
  }

  @Test
  void rejectsNullMessageType() {
    YamlNoticeTemplateRegistryAdapter adapter =
        new YamlNoticeTemplateRegistryAdapter(new NoticeTemplateProperties());

    assertThatThrownBy(() -> adapter.resolve(null, MessageDeliveryChannel.SMS, "en"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("messageType");
  }

  @Test
  void rejectsNullChannel() {
    YamlNoticeTemplateRegistryAdapter adapter =
        new YamlNoticeTemplateRegistryAdapter(new NoticeTemplateProperties());

    assertThatThrownBy(
            () -> adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, null, "en"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("channel");
  }

  @Test
  void rejectsNullPropertiesInConstructor() {
    assertThatThrownBy(() -> new YamlNoticeTemplateRegistryAdapter(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("properties");
  }

  private YamlNoticeTemplateRegistryAdapter adapterWith(
      Map.Entry<String, NoticeDefinition> entry) {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    properties.setDefinitions(Map.of(entry.getKey(), entry.getValue()));
    return new YamlNoticeTemplateRegistryAdapter(properties);
  }

  private Map.Entry<String, NoticeDefinition> registrationOtpDefinition() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(
        Map.of(
            "en", "Example registration OTP: {code}",
            "zh_HK", "範例註冊一次性密碼：{code}"));
    return Map.entry("registration-otp", definition);
  }

  private Map.Entry<String, NoticeDefinition> usernameRecoveryDefinition() {
    NoticeDefinition definition = new NoticeDefinition();
    EmailContent content = new EmailContent();
    content.setSubject("Example Username Recovery Subject");
    content.setBody("Example body for {username} at {schemeName}");
    definition.setEmail(Map.of("en", content));
    return Map.entry("username-recovery", definition);
  }
}
