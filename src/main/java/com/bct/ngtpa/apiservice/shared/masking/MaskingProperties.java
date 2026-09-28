package com.bct.ngtpa.apiservice.shared.masking;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized masking policy configuration.
 *
 * <p>Backs {@link MaskingService}. Email and mobile masking share the same
 * {@link EmailMobile} shape: both always reveal only the first and last
 * character of the relevant segment, with the hidden-character count driven by
 * {@code mode} (variable-length vs. fixed-length). See {@link MaskingService}
 * for the full masking contract.
 */
@ConfigurationProperties(prefix = "masking")
@Getter
@Setter
public class MaskingProperties {

  private EmailMobile email = new EmailMobile();
  private EmailMobile mobile = new EmailMobile();
  private Identity identity = new Identity();
  private DateOfBirth dateOfBirth = new DateOfBirth();
  private Logging logging = new Logging();
  private String disabledPlaceholder = "<masked>";

  /** Shared configuration shape for email and mobile masking. */
  @Getter
  @Setter
  public static class EmailMobile {
    private boolean enabled = true;
    private MaskMode mode = MaskMode.VARIABLE_LENGTH;
    private int maskLength = 4;
    private String maskCharacter = "*";
  }

  /** Configuration for identity (e.g. policy/cert reference) masking. */
  @Getter
  @Setter
  public static class Identity {
    private boolean enabled = true;
    private String maskCharacter = "*";
  }

  /** Configuration for date-of-birth masking (always fully redacted). */
  @Getter
  @Setter
  public static class DateOfBirth {
    private boolean enabled = true;
    private String placeholder = "<redacted>";
  }

  /** Configuration for generic structured-logging redaction placeholders. */
  @Getter
  @Setter
  public static class Logging {
    private boolean enabled = true;
    private String maskCharacter = "*";
    private String placeholder = "***";

    /**
     * Field-name substrings (case-insensitive, non-alphanumeric-stripped) that mark a
     * structured-log field or plain-text key=value assignment as sensitive and therefore
     * subject to full redaction via {@code placeholder}. Migrated from the former
     * logging-sanitizer.sensitive-tokens key (LoggingSanitizerProperties, now deleted) so
     * all masking-related configuration lives under this single masking.* tree.
     */
    private List<String> sensitiveTokens =
        new ArrayList<>(
            List.of(
                "password",
                "clientSecret",
                "client_secret",
                "clientId",
                "client_id",
                "apiKey",
                "apikey",
                "token",
                "secret",
                "policyNo",
                "policy_no",
                "policy-no",
                "certificate",
                "cert"));

    public void setSensitiveTokens(List<String> sensitiveTokens) {
      this.sensitiveTokens =
          sensitiveTokens == null ? new ArrayList<>() : new ArrayList<>(sensitiveTokens);
    }
  }
}
