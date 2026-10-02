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

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
  private static final String DEFAULT_LOCALE = "en";

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
    String resolvedLocale = resolveLocale(locale);
    return switch (channel) {
      case SMS -> resolveSms(messageType, definition, resolvedLocale);
      case EMAIL -> resolveEmail(messageType, definition, resolvedLocale);
    };
  }

  private NoticeTemplateDefinition resolveSms(
      MessageDeliveryType messageType,
      NoticeTemplateProperties.NoticeDefinition definition,
      String locale) {
    String body = lookupWithFallback(definition.getSms(), locale, messageType, "sms");
    return new NoticeTemplateDefinition(
        messageType, MessageDeliveryChannel.SMS, locale, null, body, placeholders(body));
  }

  private NoticeTemplateDefinition resolveEmail(
      MessageDeliveryType messageType,
      NoticeTemplateProperties.NoticeDefinition definition,
      String locale) {
    Map<String, NoticeTemplateProperties.EmailContent> emailMap = definition.getEmail();
    NoticeTemplateProperties.EmailContent content = emailMap.get(locale);
    if (content == null) {
      content = emailMap.get(DEFAULT_LOCALE);
    }
    if (content == null) {
      throw new IllegalStateException(
          "No notice-templates.definitions."
              + toKebabCase(messageType)
              + ".email entry for locale "
              + locale
              + " or fallback "
              + DEFAULT_LOCALE);
    }
    Set<String> required = new LinkedHashSet<>(placeholders(content.getSubject()));
    required.addAll(placeholders(content.getBody()));
    return new NoticeTemplateDefinition(
        messageType,
        MessageDeliveryChannel.EMAIL,
        locale,
        content.getSubject(),
        content.getBody(),
        required);
  }

  private String lookupWithFallback(
      Map<String, String> localized, String locale, MessageDeliveryType messageType, String channelName) {
    String value = localized.get(locale);
    if (value == null) {
      value = localized.get(DEFAULT_LOCALE);
    }
    if (value == null) {
      throw new IllegalStateException(
          "No notice-templates.definitions."
              + toKebabCase(messageType)
              + "."
              + channelName
              + " entry for locale "
              + locale
              + " or fallback "
              + DEFAULT_LOCALE);
    }
    return value;
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

  private String resolveLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return DEFAULT_LOCALE;
    }
    String normalized = locale.trim().replace('-', '_');
    return normalized.equalsIgnoreCase("zh_HK") ? "zh_HK" : normalized.toLowerCase(Locale.ROOT);
  }

  private String toKebabCase(MessageDeliveryType messageType) {
    return messageType.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
