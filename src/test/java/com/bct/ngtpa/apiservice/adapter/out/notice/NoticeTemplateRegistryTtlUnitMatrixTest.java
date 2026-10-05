package com.bct.ngtpa.apiservice.adapter.out.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Per-locale TTL unit against the REAL {@code application.yml} (solution design section 8.6),
 * loaded the same way as {@code NoticeTemplateRegistryFullMatrixSmokeTest}.
 */
class NoticeTemplateRegistryTtlUnitMatrixTest {

  private static final List<MessageDeliveryType> ALL_MESSAGE_TYPES =
      List.of(
          MessageDeliveryType.REGISTRATION_OTP,
          MessageDeliveryType.LOGIN_OTP,
          MessageDeliveryType.FORGOT_PASSWORD_OTP,
          MessageDeliveryType.USERNAME_RECOVERY);

  private static final List<MessageDeliveryChannel> ALL_CHANNELS =
      List.of(MessageDeliveryChannel.SMS, MessageDeliveryChannel.EMAIL);

  private final NoticeTemplateProperties properties = loadRealNoticeTemplateProperties();
  private final YamlNoticeTemplateRegistryAdapter adapter =
      new YamlNoticeTemplateRegistryAdapter(properties);

  @Test
  void everyMessageTypeChannelAndLocaleCarriesTheUnitOfItsLocale() {
    for (MessageDeliveryType messageType : ALL_MESSAGE_TYPES) {
      for (MessageDeliveryChannel channel : ALL_CHANNELS) {
        for (String locale : List.of("en", "zh_HK")) {
          NoticeTemplateDefinition definition = adapter.resolve(messageType, channel, locale);
          String expected = "en".equals(locale) ? "minutes" : "分鐘";
          assertThat(definition.ttlUnit())
              .as("ttlUnit of %s/%s/%s", messageType, channel, locale)
              .isEqualTo(expected);
        }
      }
    }
  }

  @Test
  void otherChineseTagsCarryTheHongKongUnitAndOtherTagsTheEnglishOne() {
    for (MessageDeliveryChannel channel : ALL_CHANNELS) {
      for (String tag : new String[] {"zh", "zh-TW", "zh-Hant", "zh-CN", "zh-Hans"}) {
        NoticeTemplateDefinition definition =
            adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, channel, tag);
        assertThat(definition.locale()).as("locale for %s/%s", channel, tag).isEqualTo("zh_HK");
        assertThat(definition.ttlUnit()).as("ttlUnit for %s/%s", channel, tag).isEqualTo("分鐘");
      }
      for (String tag : new String[] {"fr", "und", "", null}) {
        NoticeTemplateDefinition definition =
            adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, channel, tag);
        assertThat(definition.locale()).as("locale for %s/%s", channel, tag).isEqualTo("en");
        assertThat(definition.ttlUnit()).as("ttlUnit for %s/%s", channel, tag).isEqualTo("minutes");
      }
    }
  }

  @Test
  void realApplicationYamlConfiguresAUnitForEveryLocaleKeyOfEveryDefinition() {
    Map<String, String> units = properties.getTtlUnits();
    assertThat(units.get("en")).as("ttl-units.en").isEqualTo("minutes");
    assertThat(units.get("zh_HK")).as("ttl-units.zh_HK").isEqualTo("分鐘");
    for (Map.Entry<String, NoticeTemplateProperties.NoticeDefinition> entry :
        properties.getDefinitions().entrySet()) {
      for (String locale : entry.getValue().getSms().keySet()) {
        assertThat(units.get(locale)).as("unit for %s sms %s", entry.getKey(), locale).isNotBlank();
      }
      for (String locale : entry.getValue().getEmail().keySet()) {
        assertThat(units.get(locale))
            .as("unit for %s email %s", entry.getKey(), locale)
            .isNotBlank();
      }
    }
  }

  private static NoticeTemplateProperties loadRealNoticeTemplateProperties() {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> sources;
    try {
      sources = loader.load("application.yml", new ClassPathResource("application.yml"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to load application.yml from test classpath", e);
    }
    MutablePropertySources propertySources = new MutablePropertySources();
    sources.forEach(propertySources::addLast);
    Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
    return binder
        .bind("notice-templates", NoticeTemplateProperties.class)
        .orElseThrow(
            () -> new IllegalStateException("notice-templates did not bind from application.yml"));
  }
}
