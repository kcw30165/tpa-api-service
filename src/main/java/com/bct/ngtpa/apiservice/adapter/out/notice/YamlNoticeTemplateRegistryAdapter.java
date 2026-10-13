package com.bct.ngtpa.apiservice.adapter.out.notice;

import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlNoticeTemplateRegistryAdapter implements NoticeTemplateRegistryPort {

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");
  private static final String DEFAULT_LOCALE = "en";
  private static final String TTL_UNIT_PLACEHOLDER = "ttlUnit";

  private final NoticeTemplateProperties properties;

  public YamlNoticeTemplateRegistryAdapter(NoticeTemplateProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  public NoticeTemplateDefinition resolve(
      MessageDeliveryType messageType, MessageDeliveryChannel channel, String locale) {
    Objects.requireNonNull(messageType, "messageType must not be null");
    Objects.requireNonNull(channel, "channel must not be null");
    String key = toKebabCase(messageType);
    NoticeTemplateProperties.NoticeDefinition definition = properties.getDefinitions().get(key);
    if (definition == null) {
      throw new IllegalStateException("No notice-templates.definitions entry for " + key);
    }
    return switch (channel) {
      case SMS -> resolveSms(messageType, definition, locale);
      case EMAIL -> resolveEmail(messageType, definition, locale);
    };
  }

  private NoticeTemplateDefinition resolveSms(
      MessageDeliveryType messageType,
      NoticeTemplateProperties.NoticeDefinition definition,
      String locale) {
    Map<String, String> localized = definition.getSms();
    String selected = selectLocale(keysOf(localized), locale, messageType, "sms");
    String body = localized.get(selected);
    Set<String> required = placeholders(body);
    return new NoticeTemplateDefinition(
        messageType,
        MessageDeliveryChannel.SMS,
        selected,
        null,
        body,
        required,
        ttlUnitFor(messageType, "sms", selected, required));
  }

  private NoticeTemplateDefinition resolveEmail(
      MessageDeliveryType messageType,
      NoticeTemplateProperties.NoticeDefinition definition,
      String locale) {
    Map<String, NoticeTemplateProperties.EmailContent> emailMap = definition.getEmail();
    String selected = selectLocale(keysOf(emailMap), locale, messageType, "email");
    NoticeTemplateProperties.EmailContent content = emailMap.get(selected);
    Set<String> required = new LinkedHashSet<>(placeholders(content.getSubject()));
    required.addAll(placeholders(content.getBody()));
    return new NoticeTemplateDefinition(
        messageType,
        MessageDeliveryChannel.EMAIL,
        selected,
        content.getSubject(),
        content.getBody(),
        required,
        ttlUnitFor(messageType, "email", selected, required));
  }

  /**
   * Selects the configured locale key for the requested tag: exact match (case-insensitive, {@code
   * _} and {@code -} treated alike), then the first configured key with the same language, then
   * {@value #DEFAULT_LOCALE}. Nothing here lists locales, so a newly configured locale needs no
   * code change. See docs/design/10-template-service/template-service-solution-design.md section
   * 8.4.
   */
  private String selectLocale(
      Set<String> available,
      String requested,
      MessageDeliveryType messageType,
      String channelName) {
    String wanted = requested == null ? "" : requested.trim();
    if (!wanted.isEmpty()) {
      String exact = findExactLocale(available, canonical(wanted));
      if (exact != null) {
        return exact;
      }
      String sameLanguage = findLanguageLocale(available, languageOf(wanted));
      if (sameLanguage != null) {
        return sameLanguage;
      }
    }
    return defaultLocaleOrThrow(available, wanted, messageType, channelName);
  }

  private String findExactLocale(Set<String> available, String wantedCanonical) {
    for (String candidate : available) {
      if (canonical(candidate).equals(wantedCanonical)) {
        return candidate;
      }
    }
    return null;
  }

  private String findLanguageLocale(Set<String> available, String wantedLanguage) {
    if (wantedLanguage.isEmpty()) {
      return null;
    }
    for (String candidate : available) {
      if (languageOf(candidate).equals(wantedLanguage)) {
        return candidate;
      }
    }
    return null;
  }

  private String defaultLocaleOrThrow(
      Set<String> available, String wanted, MessageDeliveryType messageType, String channelName) {
    if (available.contains(DEFAULT_LOCALE)) {
      return DEFAULT_LOCALE;
    }
    throw new IllegalStateException(
        "No notice-templates.definitions."
            + toKebabCase(messageType)
            + "."
            + channelName
            + " entry for locale "
            + (wanted.isEmpty() ? "(none)" : wanted)
            + " or fallback "
            + DEFAULT_LOCALE);
  }

  private Set<String> placeholders(String template) {
    Set<String> found = new LinkedHashSet<>();
    if (template == null) {
      return found;
    }
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    return found;
  }

  private static Set<String> keysOf(Map<String, ?> localized) {
    return localized == null ? Set.of() : localized.keySet();
  }

  private static String canonical(String tag) {
    return tag.trim().replace('_', '-').toLowerCase(Locale.ROOT);
  }

  private static String languageOf(String tag) {
    return Locale.forLanguageTag(tag.trim().replace('_', '-')).getLanguage();
  }

  /**
   * Returns the unit word configured for the SELECTED locale key (solution design section 8.6),
   * never for the requested tag, so a {@code zh} request that selected {@code zh_HK} content gets
   * the {@code zh_HK} unit. Content that contains {@code {ttlUnit}} fails when no unit is
   * configured for that key; for other content a missing or blank unit is simply null.
   */
  private String ttlUnitFor(
      MessageDeliveryType messageType,
      String channelName,
      String selectedKey,
      Set<String> required) {
    Map<String, String> units = properties.getTtlUnits();
    String unit = units == null ? null : units.get(selectedKey);
    if (unit != null && unit.isBlank()) {
      unit = null;
    }
    if (unit == null && required.contains(TTL_UNIT_PLACEHOLDER)) {
      throw new IllegalStateException(
          "No notice-templates.ttl-units entry for locale "
              + selectedKey
              + " (required by notice-templates.definitions."
              + toKebabCase(messageType)
              + "."
              + channelName
              + ")");
    }
    return unit;
  }

  private String toKebabCase(MessageDeliveryType messageType) {
    return messageType.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
