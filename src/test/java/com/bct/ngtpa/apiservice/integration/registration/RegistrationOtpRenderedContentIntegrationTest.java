package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.notice.YamlNoticeTemplateRegistryAdapter;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationMessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Mono;

/**
 * Registration OTP content end to end (solution design section 8.6, plan stage II-2.c): the REAL
 * {@code application.yml}, the REAL {@link YamlNoticeTemplateRegistryAdapter}, the REAL {@link
 * RegistrationMessageDeliveryAdapter} and the REAL {@link MessageDeliveryAdapter} with its default
 * renderer. Only the two senders are replaced, by recorders, so that the text that would reach a
 * member can be asserted for SMS and EMAIL in every language the registry selects.
 */
class RegistrationOtpRenderedContentIntegrationTest {

  private static final String CODE = "123456";
  private static final String SMS_RECIPIENT = "91234567";
  private static final String EMAIL_RECIPIENT = "user@example.com";
  private static final String[] ENGLISH_FALLBACK_TAGS = {"fr", "und", "", "   ", null};
  private static final String[] OTHER_CHINESE_TAGS = {
    "zh", "zh-TW", "zh-Hant", "zh-Hant-HK", "zh-CN", "zh-Hans", "ZH_hk"
  };

  // ----- SMS ----------------------------------------------------------------------------------

  @Test
  void englishSmsKeepsASpaceBetweenTheNumberAndTheUnit() {
    Chain chain = new Chain(realProperties());

    assertThat(chain.sendSms(300, "en")).isTrue();

    assertThat(chain.smsCalls.get()).isEqualTo(1);
    assertThat(chain.emailCalls.get()).isEqualTo(0);
    assertThat(chain.sms.get().recipient()).isEqualTo(SMS_RECIPIENT);
    assertThat(chain.sms.get().content())
        .contains(CODE)
        .contains("Valid for 5 minutes.")
        .doesNotContain("{")
        .doesNotContain("}");
  }

  @Test
  void hongKongSmsWritesTheChineseUnitDirectlyAfterTheNumber() {
    Chain chain = new Chain(realProperties());

    assertThat(chain.sendSms(300, "zh-HK")).isTrue();

    assertThat(chain.sms.get().content())
        .contains(CODE)
        .contains("有效期為5分鐘。")
        .doesNotContain("minutes")
        .doesNotContain("{")
        .doesNotContain("}");
  }

  @Test
  void everyOtherChineseTagRendersTheSameSmsAsHongKong() {
    Chain reference = new Chain(realProperties());
    reference.sendSms(300, "zh-HK");
    String expected = reference.sms.get().content();

    for (String tag : OTHER_CHINESE_TAGS) {
      Chain chain = new Chain(realProperties());
      assertThat(chain.sendSms(300, tag)).as("delivery for %s", tag).isTrue();
      assertThat(chain.sms.get().content())
          .as("sms for %s", tag)
          .isEqualTo(expected)
          .contains("5分鐘")
          .doesNotContain("minutes");
    }
  }

  @Test
  void unsupportedOrMissingTagsRenderTheEnglishSms() {
    Chain reference = new Chain(realProperties());
    reference.sendSms(300, "en");
    String expected = reference.sms.get().content();

    for (String tag : ENGLISH_FALLBACK_TAGS) {
      Chain chain = new Chain(realProperties());
      assertThat(chain.sendSms(300, tag)).as("delivery for %s", tag).isTrue();
      assertThat(chain.sms.get().content()).as("sms for %s", tag).isEqualTo(expected);
    }
  }

  // ----- EMAIL --------------------------------------------------------------------------------

  @Test
  void englishEmailCarriesTheRegistrySubjectAndTheEnglishUnit() {
    Chain chain = new Chain(realProperties());

    assertThat(chain.sendEmail(300, "en")).isTrue();

    EmailMessage message = chain.email.get();
    NoticeTemplateDefinition definition = chain.definition(MessageDeliveryChannel.EMAIL, "en");
    assertThat(message.recipient()).isEqualTo(EMAIL_RECIPIENT);
    assertThat(message.subject()).isEqualTo(definition.subject());
    assertThat(message.body())
        .contains(CODE)
        .contains("5 minutes")
        .doesNotContain("{")
        .doesNotContain("}");
    assertThat(chain.smsCalls.get()).isEqualTo(0);
  }

  @Test
  void hongKongEmailCarriesTheChineseSubjectAndTheChineseUnit() {
    Chain chain = new Chain(realProperties());

    assertThat(chain.sendEmail(300, "zh-HK")).isTrue();

    EmailMessage message = chain.email.get();
    NoticeTemplateDefinition chinese = chain.definition(MessageDeliveryChannel.EMAIL, "zh-HK");
    NoticeTemplateDefinition english = chain.definition(MessageDeliveryChannel.EMAIL, "en");
    assertThat(message.subject()).isEqualTo(chinese.subject());
    assertThat(message.subject()).isNotEqualTo(english.subject());
    assertThat(message.body())
        .contains(CODE)
        .contains("此密碼於5分鐘內有效。")
        .doesNotContain("minutes")
        .doesNotContain("{")
        .doesNotContain("}");
  }

  // ----- the unit and the value ---------------------------------------------------------------

  @Test
  void theUnitComesFromTheConfiguredYamlAndNotFromTheCode() {
    NoticeTemplateProperties properties = realProperties();
    Map<String, String> units = new LinkedHashMap<>();
    units.put("en", "mins");
    units.put("zh_HK", "MIN");
    properties.setTtlUnits(units);

    Chain english = new Chain(properties);
    english.sendSms(300, "en");
    Chain chinese = new Chain(properties);
    chinese.sendSms(300, "zh-TW");

    assertThat(english.sms.get().content()).contains("Valid for 5 mins.");
    assertThat(chinese.sms.get().content()).contains("有效期為5MIN。");
  }

  @Test
  void ttlValueIsWholeMinutesWithAMinimumOfOne() {
    int[][] cases = {{0, 1}, {30, 1}, {119, 1}, {120, 2}, {300, 5}, {600, 10}};

    for (int[] pair : cases) {
      Chain chain = new Chain(realProperties());
      assertThat(chain.sendSms(pair[0], "en")).isTrue();
      assertThat(chain.sms.get().content())
          .as("sms for %d seconds", pair[0])
          .contains("Valid for " + pair[1] + " ");
    }
  }

  // ----- nothing is left unreplaced -----------------------------------------------------------

  @Test
  void noLanguageAndNoChannelLeavesAPlaceholderBehind() {
    String[] tags = {"en", "zh-HK", "zh", "zh-TW", "fr", "", null};

    for (String tag : tags) {
      Chain sms = new Chain(realProperties());
      assertThat(sms.sendSms(300, tag)).as("sms delivery for %s", tag).isTrue();
      assertThat(sms.sms.get().content()).as("sms for %s", tag).doesNotContain("{");
      assertThat(sms.sms.get().content()).as("sms for %s", tag).doesNotContain("}");

      Chain email = new Chain(realProperties());
      assertThat(email.sendEmail(300, tag)).as("email delivery for %s", tag).isTrue();
      assertThat(email.email.get().subject()).as("subject for %s", tag).doesNotContain("{");
      assertThat(email.email.get().body()).as("body for %s", tag).doesNotContain("{");
      assertThat(email.email.get().body()).as("body for %s", tag).doesNotContain("}");
    }
  }

  // ----- decision D7 and rule 4 of the design, end to end -------------------------------------

  @Test
  void aMissingRegistrationDefinitionFailsBeforeAnythingIsSent() {
    NoticeTemplateProperties properties = realProperties();
    properties.setDefinitions(new LinkedHashMap<>());
    Chain chain = new Chain(properties);

    assertThatThrownBy(() -> chain.sendSms(300, "en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("registration-otp");
    assertThatThrownBy(() -> chain.sendEmail(300, "zh-HK"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("registration-otp");

    assertThat(chain.smsCalls.get()).isEqualTo(0);
    assertThat(chain.emailCalls.get()).isEqualTo(0);
  }

  @Test
  void aRequiredButUnconfiguredUnitFailsBeforeAnythingIsSent() {
    NoticeTemplateProperties properties = realProperties();
    properties.setTtlUnits(new LinkedHashMap<>());
    Chain chain = new Chain(properties);

    assertThatThrownBy(() -> chain.sendSms(300, "zh-HK"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-units");

    assertThat(chain.smsCalls.get()).isEqualTo(0);
  }

  // ----- the chain under test -----------------------------------------------------------------

  private static NoticeTemplateProperties realProperties() {
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

  /** The real registry, adapter and delivery service, with recording senders. */
  private static final class Chain {
    final AtomicInteger smsCalls = new AtomicInteger();
    final AtomicInteger emailCalls = new AtomicInteger();
    final AtomicReference<SmsMessage> sms = new AtomicReference<>();
    final AtomicReference<EmailMessage> email = new AtomicReference<>();
    final YamlNoticeTemplateRegistryAdapter registry;
    final RegistrationMessageDeliveryAdapter gateway;

    Chain(NoticeTemplateProperties properties) {
      registry = new YamlNoticeTemplateRegistryAdapter(properties);
      MessageDeliveryAdapter delivery =
          new MessageDeliveryAdapter(
              message -> {
                smsCalls.incrementAndGet();
                sms.set(message);
                return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-1", null, null));
              },
              message -> {
                emailCalls.incrementAndGet();
                email.set(message);
                return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "mail-1", null, null));
              });
      gateway = new RegistrationMessageDeliveryAdapter(delivery, registry);
    }

    Boolean sendSms(int ttlSeconds, String language) {
      return gateway
          .deliverRegistrationOtp(
              RegistrationContactType.MOBILE, SMS_RECIPIENT, CODE, ttlSeconds, language, "req-1")
          .block();
    }

    Boolean sendEmail(int ttlSeconds, String language) {
      return gateway
          .deliverRegistrationOtp(
              RegistrationContactType.EMAIL, EMAIL_RECIPIENT, CODE, ttlSeconds, language, "req-2")
          .block();
    }

    NoticeTemplateDefinition definition(MessageDeliveryChannel channel, String language) {
      return registry.resolve(MessageDeliveryType.REGISTRATION_OTP, channel, language);
    }
  }
}
