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
 * The unit returned with a resolved definition is the one configured for the locale key that the
 * registry SELECTED, never for the requested tag (solution design sections 8.4 and 8.6).
 */
class YamlNoticeTemplateRegistryAdapterTtlUnitTest {

  private static final MessageDeliveryType TYPE = MessageDeliveryType.REGISTRATION_OTP;
  private static final String EN = "EN {code} {ttlValue} {ttlUnit}";
  private static final String ZH = "ZH {code} {ttlValue}{ttlUnit}";

  @Test
  void exactMatchReturnsTheUnitOfThatKey() {
    YamlNoticeTemplateRegistryAdapter adapter = adapter(units(), smsEnAndZh(), emailEnAndZh());

    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "en").ttlUnit()).isEqualTo("minutes");
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "zh-HK").ttlUnit()).isEqualTo("分鐘");
    assertThat(resolve(adapter, MessageDeliveryChannel.SMS, "ZH_hk").ttlUnit()).isEqualTo("分鐘");
  }

  @Test
  void sameLanguageFallbackReturnsTheUnitOfTheSelectedKeyNotTheRequestedTag() {
    YamlNoticeTemplateRegistryAdapter adapter = adapter(units(), smsEnAndZh(), emailEnAndZh());

    for (String tag : new String[] {"zh", "zh-TW", "zh-Hant", "zh-CN", "zh-Hans"}) {
      NoticeTemplateDefinition sms = resolve(adapter, MessageDeliveryChannel.SMS, tag);
      assertThat(sms.locale()).as("sms locale for %s", tag).isEqualTo("zh_HK");
      assertThat(sms.ttlUnit()).as("sms unit for %s", tag).isEqualTo("分鐘");
    }
  }

  @Test
  void aRequestThatFallsBackToEnglishReturnsTheEnglishUnit() {
    YamlNoticeTemplateRegistryAdapter adapter = adapter(units(), smsEnAndZh(), emailEnAndZh());

    for (String tag : new String[] {"fr", "und", "", null}) {
      NoticeTemplateDefinition sms = resolve(adapter, MessageDeliveryChannel.SMS, tag);
      assertThat(sms.locale()).as("sms locale for %s", tag).isEqualTo("en");
      assertThat(sms.ttlUnit()).as("sms unit for %s", tag).isEqualTo("minutes");
    }
  }

  @Test
  void emailContentCarriesTheUnitOfTheSelectedKey() {
    YamlNoticeTemplateRegistryAdapter adapter = adapter(units(), smsEnAndZh(), emailEnAndZh());

    NoticeTemplateDefinition chinese = resolve(adapter, MessageDeliveryChannel.EMAIL, "zh-TW");
    assertThat(chinese.locale()).isEqualTo("zh_HK");
    assertThat(chinese.ttlUnit()).isEqualTo("分鐘");

    NoticeTemplateDefinition english = resolve(adapter, MessageDeliveryChannel.EMAIL, "en-US");
    assertThat(english.locale()).isEqualTo("en");
    assertThat(english.ttlUnit()).isEqualTo("minutes");
  }

  @Test
  void contentThatUsesTheUnitFailsWhenNoUnitIsConfiguredForTheSelectedKey() {
    Map<String, String> onlyEnglish = new LinkedHashMap<>();
    onlyEnglish.put("en", "minutes");
    YamlNoticeTemplateRegistryAdapter adapter = adapter(onlyEnglish, smsEnAndZh(), emailEnAndZh());

    assertThatThrownBy(() -> resolve(adapter, MessageDeliveryChannel.SMS, "zh-HK"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("zh_HK")
        .hasMessageContaining("registration-otp")
        .hasMessageContaining("sms");
    assertThatThrownBy(() -> resolve(adapter, MessageDeliveryChannel.EMAIL, "zh"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("zh_HK")
        .hasMessageContaining("email");
  }

  @Test
  void contentWithoutThePlaceholderCarriesTheConfiguredUnitOrNull() {
    Map<String, String> plain = new LinkedHashMap<>();
    plain.put("en", "Plain {code}");

    YamlNoticeTemplateRegistryAdapter configured = adapter(units(), plain, Map.of());
    assertThat(resolve(configured, MessageDeliveryChannel.SMS, "en").ttlUnit())
        .isEqualTo("minutes");

    YamlNoticeTemplateRegistryAdapter unconfigured = adapter(Map.of(), plain, Map.of());
    assertThat(resolve(unconfigured, MessageDeliveryChannel.SMS, "en").ttlUnit()).isNull();
  }

  @Test
  void aBlankConfiguredUnitIsAbsent() {
    Map<String, String> blank = new LinkedHashMap<>();
    blank.put("en", "   ");
    Map<String, String> plain = new LinkedHashMap<>();
    plain.put("en", "Plain {code}");
    Map<String, String> withUnit = new LinkedHashMap<>();
    withUnit.put("en", EN);

    assertThat(resolve(adapter(blank, plain, Map.of()), MessageDeliveryChannel.SMS, "en").ttlUnit())
        .isNull();
    YamlNoticeTemplateRegistryAdapter required = adapter(blank, withUnit, Map.of());
    assertThatThrownBy(() -> resolve(required, MessageDeliveryChannel.SMS, "en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("en");
  }

  @Test
  void aNullUnitsMapIsTreatedAsEmpty() {
    Map<String, String> plain = new LinkedHashMap<>();
    plain.put("en", "Plain {code}");
    Map<String, String> withUnit = new LinkedHashMap<>();
    withUnit.put("en", EN);

    YamlNoticeTemplateRegistryAdapter noUnits = adapter(null, plain, Map.of());
    assertThat(resolve(noUnits, MessageDeliveryChannel.SMS, "en").ttlUnit()).isNull();

    YamlNoticeTemplateRegistryAdapter required = adapter(null, withUnit, Map.of());
    assertThatThrownBy(() -> resolve(required, MessageDeliveryChannel.SMS, "en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("en");
  }

  private static NoticeTemplateDefinition resolve(
      YamlNoticeTemplateRegistryAdapter adapter, MessageDeliveryChannel channel, String locale) {
    return adapter.resolve(TYPE, channel, locale);
  }

  private static YamlNoticeTemplateRegistryAdapter adapter(
      Map<String, String> units, Map<String, String> sms, Map<String, EmailContent> email) {
    NoticeDefinition definition = new NoticeDefinition();
    definition.setSms(sms);
    definition.setEmail(email);
    NoticeTemplateProperties properties = new NoticeTemplateProperties();
    properties.setDefinitions(Map.of("registration-otp", definition));
    properties.setTtlUnits(units);
    return new YamlNoticeTemplateRegistryAdapter(properties);
  }

  private static Map<String, String> units() {
    Map<String, String> units = new LinkedHashMap<>();
    units.put("en", "minutes");
    units.put("zh_HK", "分鐘");
    return units;
  }

  private static Map<String, String> smsEnAndZh() {
    Map<String, String> sms = new LinkedHashMap<>();
    sms.put("en", EN);
    sms.put("zh_HK", ZH);
    return sms;
  }

  private static Map<String, EmailContent> emailEnAndZh() {
    Map<String, EmailContent> email = new LinkedHashMap<>();
    email.put("en", email("subject en", EN));
    email.put("zh_HK", email("subject zh", ZH));
    return email;
  }

  private static EmailContent email(String subject, String body) {
    EmailContent content = new EmailContent();
    content.setSubject(subject);
    content.setBody(body);
    return content;
  }
}
