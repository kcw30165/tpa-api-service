package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized notice-template content, keyed by the kebab-case form of {@code MessageDeliveryType}
 * (e.g. {@code registration-otp}). Mirrors the {@code Map<String, String>} binding style used by
 * {@code FundPriceExportProperties.sheetNames} rather than binding a Map directly at the
 * {@code @ConfigurationProperties} root, for which no codebase precedent exists.
 *
 * <p>NOTE: as of this scaffolding round, {@code application.yml} does not yet populate {@code
 * notice-templates.definitions.*} with final business-approved content. See
 * docs/design/7-template-service/template-service-solution-design.md section 8.3: the BRD's static
 * "5 minutes" wording and current production's {ttlValue}/{ttlUnit} placeholders have not been
 * reconciled.
 */
@ConfigurationProperties(prefix = "notice-templates")
@Getter
@Setter
public class NoticeTemplateProperties {

  private Map<String, NoticeDefinition> definitions = new LinkedHashMap<>();

  /**
   * Unit word for {@code {ttlUnit}} per locale key (for example {@code en} and {@code zh_HK}). The
   * unit of a definition is looked up with the locale key selected for its content (solution design
   * section 8.6).
   */
  private Map<String, String> ttlUnits = new LinkedHashMap<>();

  private static final String TTL_UNIT_PLACEHOLDER = "{ttlUnit}";

  @Getter
  @Setter
  public static class NoticeDefinition {
    private Map<String, String> sms = new LinkedHashMap<>();
    private Map<String, EmailContent> email = new LinkedHashMap<>();
  }

  @Getter
  @Setter
  public static class EmailContent {
    private String subject;
    private String body;
  }

  /**
   * Fails fast at startup when a configured definition is missing both channels, or when an email
   * locale entry has a blank subject/body. Does not require every {@code MessageDeliveryType} to be
   * configured, since this round scaffolds the mechanism only (§8.3).
   *
   * <p>It also requires a non-blank {@code ttl-units} entry under the same locale key for every
   * content text that contains {@code {ttlUnit}} (solution design section 8.6).
   */
  public void validate() {
    validateDefinitions();
    validateTtlUnits();
  }

  /**
   * Fails fast when a content text that contains {@code {ttlUnit}} has no non-blank {@code
   * ttl-units} entry under its own locale key. Content without the placeholder imposes nothing. The
   * definitions were already validated, so no definition and no e-mail content is null here.
   */
  private void validateTtlUnits() {
    for (Map.Entry<String, NoticeDefinition> entry : definitions.entrySet()) {
      NoticeDefinition definition = entry.getValue();
      if (definition.sms != null) {
        for (Map.Entry<String, String> sms : definition.sms.entrySet()) {
          requireTtlUnit(entry.getKey(), "sms", sms.getKey(), sms.getValue());
        }
      }
      if (definition.email != null) {
        for (Map.Entry<String, EmailContent> email : definition.email.entrySet()) {
          requireTtlUnit(entry.getKey(), "email", email.getKey(), email.getValue().subject);
          requireTtlUnit(entry.getKey(), "email", email.getKey(), email.getValue().body);
        }
      }
    }
  }

  private void requireTtlUnit(String definitionKey, String channel, String localeKey, String text) {
    if (!text.contains(TTL_UNIT_PLACEHOLDER)) {
      return;
    }
    String unit = ttlUnits == null ? null : ttlUnits.get(localeKey);
    if (unit == null || unit.isBlank()) {
      throw new IllegalStateException(
          "notice-templates.ttl-units."
              + localeKey
              + " must not be blank: notice-templates.definitions."
              + definitionKey
              + "."
              + channel
              + "."
              + localeKey
              + " contains {ttlUnit}");
    }
  }

  private void validateDefinitions() {
    for (Map.Entry<String, NoticeDefinition> entry : definitions.entrySet()) {
      validateDefinition(entry.getKey(), entry.getValue());
    }
  }

  private void validateDefinition(String key, NoticeDefinition definition) {
    if (definition == null) {
      throw new IllegalStateException("notice-templates.definitions." + key + " must not be null");
    }
    boolean hasSms = definition.sms != null && !definition.sms.isEmpty();
    boolean hasEmail = definition.email != null && !definition.email.isEmpty();
    if (!hasSms && !hasEmail) {
      throw new IllegalStateException(
          "notice-templates.definitions." + key + " must configure at least one channel");
    }
    validateEmailEntries(key, definition.email);
    validateSmsEntries(key, definition.sms);
  }

  private void validateEmailEntries(String key, Map<String, EmailContent> email) {
    if (email == null) {
      return;
    }
    for (Map.Entry<String, EmailContent> entry : email.entrySet()) {
      validateEmailEntry(key, entry.getKey(), entry.getValue());
    }
  }

  private void validateEmailEntry(String key, String locale, EmailContent content) {
    if (content == null
        || content.subject == null
        || content.subject.isBlank()
        || content.body == null
        || content.body.isBlank()) {
      throw new IllegalStateException(
          "notice-templates.definitions."
              + key
              + ".email."
              + locale
              + " must have non-blank subject and body");
    }
  }

  private void validateSmsEntries(String key, Map<String, String> sms) {
    if (sms == null) {
      return;
    }
    for (Map.Entry<String, String> entry : sms.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isBlank()) {
        throw new IllegalStateException(
            "notice-templates.definitions."
                + key
                + ".sms."
                + entry.getKey()
                + " must not be blank");
      }
    }
  }
}
