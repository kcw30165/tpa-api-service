package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized notice-template content, keyed by the kebab-case form of {@code
 * MessageDeliveryType} (e.g. {@code registration-otp}). Mirrors the {@code Map<String, String>}
 * binding style used by {@code FundPriceExportProperties.sheetNames} rather than binding a Map
 * directly at the {@code @ConfigurationProperties} root, for which no codebase precedent exists.
 *
 * <p>NOTE: as of this scaffolding round, {@code application.yml} does not yet populate {@code
 * notice-templates.definitions.*} with final business-approved content. See
 * docs/design/7-template-service/template-service-solution-design.md section 8.3: the BRD's
 * static "5 minutes" wording and current production's {ttlValue}/{ttlUnit} placeholders have not
 * been reconciled.
 */
@ConfigurationProperties(prefix = "notice-templates")
@Getter
@Setter
public class NoticeTemplateProperties {

  private Map<String, NoticeDefinition> definitions = new LinkedHashMap<>();

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
   * locale entry has a blank subject/body. Does not require every {@code MessageDeliveryType} to
   * be configured, since this round scaffolds the mechanism only (§8.3).
   */
  public void validate() {
    for (Map.Entry<String, NoticeDefinition> entry : definitions.entrySet()) {
      String key = entry.getKey();
      NoticeDefinition definition = entry.getValue();
      if (definition == null) {
        throw new IllegalStateException("notice-templates.definitions." + key + " must not be null");
      }
      boolean hasSms = definition.sms != null && !definition.sms.isEmpty();
      boolean hasEmail = definition.email != null && !definition.email.isEmpty();
      if (!hasSms && !hasEmail) {
        throw new IllegalStateException(
            "notice-templates.definitions." + key + " must configure at least one channel");
      }
      if (definition.email != null) {
        for (Map.Entry<String, EmailContent> emailEntry : definition.email.entrySet()) {
          EmailContent content = emailEntry.getValue();
          if (content == null
              || content.subject == null
              || content.subject.isBlank()
              || content.body == null
              || content.body.isBlank()) {
            throw new IllegalStateException(
                "notice-templates.definitions."
                    + key
                    + ".email."
                    + emailEntry.getKey()
                    + " must have non-blank subject and body");
          }
        }
      }
      if (definition.sms != null) {
        for (Map.Entry<String, String> smsEntry : definition.sms.entrySet()) {
          if (smsEntry.getValue() == null || smsEntry.getValue().isBlank()) {
            throw new IllegalStateException(
                "notice-templates.definitions." + key + ".sms." + smsEntry.getKey()
                    + " must not be blank");
          }
        }
      }
    }
  }
}
