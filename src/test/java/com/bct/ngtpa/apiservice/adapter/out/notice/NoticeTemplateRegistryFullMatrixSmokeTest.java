package com.bct.ngtpa.apiservice.adapter.out.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Phase I, Stage I-6 (final stage of the Template Service's own implementation, per
 * docs/design/10-template-service/template-service-implementation-plan.md): confirms {@link
 * YamlNoticeTemplateRegistryAdapter#resolve} returns correct content for ALL FOUR {@link
 * MessageDeliveryType} values x 2 {@link MessageDeliveryChannel} values x 2 locales, using the
 * REAL {@code application.yml} (loaded the same way as {@code
 * ApplicationYamlNoticeTemplatesBindingTest}: {@link YamlPropertySourceLoader} + {@link Binder},
 * no synthetic test-only properties), wired through the REAL adapter class - not a hand-built
 * {@link NoticeTemplateProperties} instance. This is the end-to-end correctness gate that closes
 * Phase I before any workflow migration (Phase II) may begin.
 */
class NoticeTemplateRegistryFullMatrixSmokeTest {

  private static final List<MessageDeliveryType> ALL_MESSAGE_TYPES =
      List.of(
          MessageDeliveryType.REGISTRATION_OTP,
          MessageDeliveryType.LOGIN_OTP,
          MessageDeliveryType.FORGOT_PASSWORD_OTP,
          MessageDeliveryType.USERNAME_RECOVERY);

  private static final List<MessageDeliveryChannel> ALL_CHANNELS =
      List.of(MessageDeliveryChannel.SMS, MessageDeliveryChannel.EMAIL);

  private static final List<String> ALL_LOCALES = List.of("en", "zh_HK");

  private final YamlNoticeTemplateRegistryAdapter adapter = realAdapterFromApplicationYaml();

  @Test
  void resolvesNonNullDefinitionForEveryMessageTypeChannelLocaleCombination() {
    for (MessageDeliveryType messageType : ALL_MESSAGE_TYPES) {
      for (MessageDeliveryChannel channel : ALL_CHANNELS) {
        for (String locale : ALL_LOCALES) {
          NoticeTemplateDefinition definition = adapter.resolve(messageType, channel, locale);
          assertThat(definition)
              .as("resolve(%s, %s, %s)", messageType, channel, locale)
              .isNotNull();
          assertThat(definition.messageType()).isEqualTo(messageType);
          assertThat(definition.channel()).isEqualTo(channel);
          assertThat(definition.locale()).isEqualTo(locale);
          assertThat(definition.body()).isNotBlank();
        }
      }
    }
  }

  @Test
  void emailResolutionsAlwaysHaveNonBlankSubject() {
    for (MessageDeliveryType messageType : ALL_MESSAGE_TYPES) {
      for (String locale : ALL_LOCALES) {
        NoticeTemplateDefinition definition =
            adapter.resolve(messageType, MessageDeliveryChannel.EMAIL, locale);
        assertThat(definition.subject())
            .as("email subject for %s/%s", messageType, locale)
            .isNotBlank();
      }
    }
  }

  @Test
  void smsResolutionsHaveNullSubject() {
    for (MessageDeliveryType messageType : ALL_MESSAGE_TYPES) {
      for (String locale : ALL_LOCALES) {
        NoticeTemplateDefinition definition =
            adapter.resolve(messageType, MessageDeliveryChannel.SMS, locale);
        assertThat(definition.subject()).as("sms subject for %s/%s", messageType, locale).isNull();
      }
    }
  }

  @Test
  void registrationOtpRequiresOnlyCodeAndTtlPlaceholders() {
    NoticeTemplateDefinition sms =
        adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.SMS, "en");
    assertThat(sms.requiredPlaceholders()).containsExactlyInAnyOrder("code", "ttlValue", "ttlUnit");

    NoticeTemplateDefinition email =
        adapter.resolve(MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.EMAIL, "en");
    assertThat(email.requiredPlaceholders())
        .containsExactlyInAnyOrder("code", "ttlValue", "ttlUnit");
    assertThat(email.requiredPlaceholders()).doesNotContain("trustCode", "schemeName", "username");
  }

  @Test
  void loginAndForgotPasswordOtpRequireTrustCodeAndSchemeNamePlaceholders() {
    for (MessageDeliveryType messageType :
        List.of(MessageDeliveryType.LOGIN_OTP, MessageDeliveryType.FORGOT_PASSWORD_OTP)) {
      NoticeTemplateDefinition sms =
          adapter.resolve(messageType, MessageDeliveryChannel.SMS, "en");
      assertThat(sms.requiredPlaceholders())
          .as("%s sms placeholders", messageType)
          .containsExactlyInAnyOrder("trustCode", "code", "ttlValue", "ttlUnit");

      NoticeTemplateDefinition emailEn =
          adapter.resolve(messageType, MessageDeliveryChannel.EMAIL, "en");
      assertThat(emailEn.requiredPlaceholders())
          .as("%s email[en] placeholders", messageType)
          .containsExactlyInAnyOrder("trustCode", "code", "schemeName", "ttlValue", "ttlUnit");

      NoticeTemplateDefinition emailZh =
          adapter.resolve(messageType, MessageDeliveryChannel.EMAIL, "zh_HK");
      assertThat(emailZh.requiredPlaceholders())
          .as("%s email[zh_HK] placeholders", messageType)
          .containsExactlyInAnyOrder("code", "schemeNameChi", "ttlValue", "ttlUnit");
    }
  }

  @Test
  void usernameRecoveryRequiresNoTtlPlaceholders() {
    for (MessageDeliveryChannel channel : ALL_CHANNELS) {
      for (String locale : ALL_LOCALES) {
        NoticeTemplateDefinition definition =
            adapter.resolve(MessageDeliveryType.USERNAME_RECOVERY, channel, locale);
        assertThat(definition.requiredPlaceholders())
            .as("username-recovery %s/%s", channel, locale)
            .doesNotContain("ttlValue", "ttlUnit", "code");
      }
    }
  }

  @Test
  void loginOtpAndForgotPasswordOtpContentIsByteForByteSynchronized() {
    // Resolved decision (solution-design doc section 5): two separate config entries,
    // content intentionally identical.
    for (MessageDeliveryChannel channel : ALL_CHANNELS) {
      for (String locale : ALL_LOCALES) {
        NoticeTemplateDefinition login =
            adapter.resolve(MessageDeliveryType.LOGIN_OTP, channel, locale);
        NoticeTemplateDefinition forgotPassword =
            adapter.resolve(MessageDeliveryType.FORGOT_PASSWORD_OTP, channel, locale);
        assertThat(login.body())
            .as("login-otp vs forgot-password-otp body, %s/%s", channel, locale)
            .isEqualTo(forgotPassword.body());
        assertThat(login.subject())
            .as("login-otp vs forgot-password-otp subject, %s/%s", channel, locale)
            .isEqualTo(forgotPassword.subject());
      }
    }
  }

  @Test
  void zhHkAndEnglishContentActuallyDiffer() {
    // Guards against an accidental "en fallback masking a real zh_HK entry" regression: if
    // zh_HK silently resolved to the English text, this fails loudly rather than passing
    // vacuously.
    for (MessageDeliveryType messageType : ALL_MESSAGE_TYPES) {
      for (MessageDeliveryChannel channel : ALL_CHANNELS) {
        NoticeTemplateDefinition en = adapter.resolve(messageType, channel, "en");
        NoticeTemplateDefinition zh = adapter.resolve(messageType, channel, "zh_HK");
        assertThat(zh.body())
            .as("%s/%s zh_HK body must differ from en body", messageType, channel)
            .isNotEqualTo(en.body());
      }
    }
  }

  @Test
  void realApplicationYamlPassesStructuralValidation() {
    // Re-asserted here (already covered standalone in script 026's
    // ApplicationYamlNoticeTemplatesBindingTest) as part of this end-to-end bundle closing
    // Phase I.
    NoticeTemplateProperties properties = loadRealNoticeTemplateProperties();
    properties.validate();
    assertThat(properties.getDefinitions())
        .containsKeys("registration-otp", "login-otp", "forgot-password-otp", "username-recovery");
  }

  private static YamlNoticeTemplateRegistryAdapter realAdapterFromApplicationYaml() {
    return new YamlNoticeTemplateRegistryAdapter(loadRealNoticeTemplateProperties());
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
