package com.bct.ngtpa.apiservice.adapter.out.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.EmailContent;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties.NoticeDefinition;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Locale selection rule of {@link YamlNoticeTemplateRegistryAdapter}: exact match, then same
 * language (first configured key), then {@code en}; the selected key is reported. See
 * docs/design/10-template-service/template-service-solution-design.md section 8.4. This class does
 * not modify the pre-existing YamlNoticeTemplateRegistryAdapterTest.
 */
class YamlNoticeTemplateRegistryAdapterLocaleSelectionTest {

  private static final MessageDeliveryType TYPE = MessageDeliveryType.USERNAME_RECOVERY;

  @Test
  void englishTagsSelectEnglishForBothChannels() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(englishAndHongKong());
    for (String tag : new String[] {"en", "EN", "en-US", "en_GB"}) {
      assertThat(resolve(adapter, MessageDeliveryChannel.SMS, tag).locale())
          .as("sms %s", tag)
          .isEqualTo("en");
      assertThat(resolve(adapter, MessageDeliveryChannel.EMAIL, tag).locale())
          .as("email %s", tag)
          .isEqualTo("en");
    }
  }

  @Test
  void unsupportedOrUndeterminedTagsFallBackToEnglish() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(englishAndHongKong());
    for (String tag : new String[] {"fr", "und", "", "   ", null}) {
      assertThat(resolve(adapter, MessageDeliveryChannel.SMS, tag).locale())
          .as("sms %s", tag)
          .isEqualTo("en");
      assertThat(resolve(adapter, MessageDeliveryChannel.EMAIL, tag).locale())
          .as("email %s", tag)
          .isEqualTo("en");
    }
  }

  @Test
  void hongKongTagsSelectZhHkRegardlessOfCaseSeparatorOrPadding() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(englishAndHongKong());
    for (String tag : new String[] {"zh_HK", "zh-HK", "zh-hk", "ZH_hk", " zh-HK "}) {
      assertThat(resolve(adapter, MessageDeliveryChannel.SMS, tag).locale())
          .as("sms %s", tag)
          .isEqualTo("zh_HK");
      assertThat(resolve(adapter, MessageDeliveryChannel.EMAIL, tag).locale())
          .as("email %s", tag)
          .isEqualTo("zh_HK");
    }
  }

  @Test
  void otherChineseTagsSelectTheConfiguredChineseLocale() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(englishAndHongKong());
    for (String tag : new String[] {"zh", "zh-TW", "zh-CN", "zh-Hant", "zh-Hant-HK", "zh-Hans"}) {
      assertThat(resolve(adapter, MessageDeliveryChannel.SMS, tag).locale())
          .as("sms %s", tag)
          .isEqualTo("zh_HK");
      assertThat(resolve(adapter, MessageDeliveryChannel.EMAIL, tag).locale())
          .as("email %s", tag)
          .isEqualTo("zh_HK");
    }
  }

  @Test
  void contentComesFromTheSelectedLocale() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(englishAndHongKong());

    NoticeTemplateDefinition sms = resolve(adapter, MessageDeliveryChannel.SMS, "zh-TW");
    assertThat(sms.body()).isEqualTo("ZH-HK {username}");
    assertThat(sms.subject()).isNull();

    NoticeTemplateDefinition email = resolve(adapter, MessageDeliveryChannel.EMAIL, "zh");
    assertThat(email.subject()).isEqualTo("subject zh_HK");
    assertThat(email.body()).isEqualTo("body zh_HK {schemeNameChi}");
    assertThat(email.requiredPlaceholders()).containsExactly("schemeNameChi");
  }

  @Test
  void reportedLocaleIsTheSelectedKeyNotTheRequestedTag() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(englishAndHongKong());
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "fr").locale()).isEqualTo("en");
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "zh-TW").locale()).isEqualTo("zh_HK");
  }

  @Test
  void exactMatchBeatsSameLanguageMatch() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(withExtraLocales());
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "zh-CN").locale()).isEqualTo("zh_CN");
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "zh-HK").locale()).isEqualTo("zh_HK");
  }

  @Test
  void sameLanguageMatchUsesTheFirstConfiguredKey() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(withExtraLocales());
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "zh-TW").locale()).isEqualTo("zh_HK");
  }

  @Test
  void aNewlyConfiguredLocaleNeedsNoCodeChange() {
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(withExtraLocales());
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "ja-JP").locale()).isEqualTo("ja");
    assertThat(resolve(adapter, MessageDeliveryChannel.EMAIL, "ja").locale()).isEqualTo("ja");
  }

  @Test
  void missingLocaleAndMissingEnglishFailsNamingMessageTypeAndChannel() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(new LinkedHashMap<>(Map.of("fr", "FR")));
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(definition);

    assertThatThrownBy(() -> resolve(adapter, MessageDeliveryChannel.SMS, "de"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("username-recovery")
        .hasMessageContaining("sms")
        .hasMessageContaining("de");
  }

  @Test
  void noRequestedLocaleAndNoEnglishFailsWithReadableMessage() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(new LinkedHashMap<>(Map.of("fr", "FR")));
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(definition);

    assertThatThrownBy(() -> resolve(adapter, MessageDeliveryChannel.SMS, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("(none)");
  }

  @Test
  void anAbsentChannelMapFailsWithIllegalStateNotNullPointer() {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(null);
    YamlNoticeTemplateRegistryAdapter adapter = adapterWith(definition);

    assertThatThrownBy(() -> resolve(adapter, MessageDeliveryChannel.SMS, "en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("username-recovery");
  }

  private static NoticeTemplateDefinition resolve(
      YamlNoticeTemplateRegistryAdapter adapter, MessageDeliveryChannel channel, String locale) {
    return adapter.resolve(TYPE, channel, locale);
  }

  private static YamlNoticeTemplateRegistryAdapter adapterWith(NoticeDefinition definition) {
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    properties.setDefinitions(Map.of("username-recovery", definition));
    return new YamlNoticeTemplateRegistryAdapter(properties);
  }

  private static NoticeDefinition englishAndHongKong() {
    NoticeDefinition definition = new NoticeDefinition();
    Map<String, String> sms = new LinkedHashMap<>();
    sms.put("en", "EN {username}");
    sms.put("zh_HK", "ZH-HK {username}");
    definition.setSms(sms);
    Map<String, EmailContent> email = new LinkedHashMap<>();
    email.put("en", email("subject en", "body en {schemeName}"));
    email.put("zh_HK", email("subject zh_HK", "body zh_HK {schemeNameChi}"));
    definition.setEmail(email);
    return definition;
  }

  private static NoticeDefinition withExtraLocales() {
    NoticeDefinition definition = englishAndHongKong();
    definition.getSms().put("zh_CN", "ZH-CN {username}");
    definition.getSms().put("ja", "JA {username}");
    definition.getEmail().put("zh_CN", email("subject zh_CN", "body zh_CN"));
    definition.getEmail().put("ja", email("subject ja", "body ja"));
    return definition;
  }

  private static EmailContent email(String subject, String body) {
    EmailContent content = new EmailContent();
    content.setSubject(subject);
    content.setBody(body);
    return content;
  }
}
