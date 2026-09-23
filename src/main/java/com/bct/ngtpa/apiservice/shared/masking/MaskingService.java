package com.bct.ngtpa.apiservice.shared.masking;

import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Centralized masking policy for all email, mobile, identity, and
 * date-of-birth values that must never be exposed or logged in full.
 *
 * <p>Email and mobile masking always reveal only the first and last character
 * of the relevant segment (email: local-part before {@code @}; mobile: the
 * full number). The number of mask characters rendered between them is
 * controlled by {@code masking.email.mode} / {@code masking.mobile.mode}:
 *
 * <ul>
 *   <li>{@code variable-length} (default): mask-character count equals the
 *       actual number of hidden characters (length-preserving).
 *   <li>{@code fixed-length}: mask-character count is always the configured
 *       {@code mask-length}, regardless of input length.
 * </ul>
 *
 * <p>When a type is disabled ({@code masking.<type>.enabled=false}), the
 * configured {@code masking.disabled-placeholder} is returned instead of any
 * partial reveal, so the raw value is never exposed either way.
 *
 * <p>Identity and date-of-birth masking are always a full, non-partial
 * redaction: real identity values in this system (GPN/GPFS/STAFFNO/ID) are
 * short opaque codes with no safely-revealable substring, so
 * {@link #maskIdentity(String)} always returns the configured
 * {@code masking.disabled-placeholder} rather than attempting any partial
 * reveal.
 */
@Component
public class MaskingService {

  private final MaskingProperties properties;

  public MaskingService(MaskingProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  /**
   * Masks an email address, keeping only the first and last character of the
   * local-part (before {@code @}) visible; the domain is never masked.
   */
  public String maskEmail(String email) {
    if (!StringUtils.hasText(email)) {
      return email;
    }
    MaskingProperties.EmailMobile config = properties.getEmail();
    if (!config.isEnabled()) {
      return properties.getDisabledPlaceholder();
    }
    String trimmed = email.trim();
    int at = trimmed.indexOf('@');
    if (at <= 0) {
      throw new IllegalArgumentException("invalid email destination");
    }
    String local = trimmed.substring(0, at);
    String domain = trimmed.substring(at);
    return maskFirstLast(local, config) + domain;
  }

  /**
   * Masks a mobile number, keeping only the first and last digit visible.
   */
  public String maskMobile(String mobile) {
    if (!StringUtils.hasText(mobile)) {
      return mobile;
    }
    MaskingProperties.EmailMobile config = properties.getMobile();
    if (!config.isEnabled()) {
      return properties.getDisabledPlaceholder();
    }
    return maskFirstLast(mobile.trim(), config);
  }

  /**
   * Masks an identity/reference value, revealing only the last
   * {@code '/'}-delimited segment. Falls back to the global disabled
   * placeholder when there is no revealable segment.
   */
  public String maskIdentity(String identity) {
    if (!StringUtils.hasText(identity)) {
      return identity;
    }
    return properties.getDisabledPlaceholder();
  }

  /**
   * Masks a date-of-birth value. Always a full, non-partial redaction (never
   * reveals any digit of the original value).
   */
  public String maskDateOfBirth(String dateOfBirth) {
    if (!StringUtils.hasText(dateOfBirth)) {
      return dateOfBirth;
    }
    MaskingProperties.DateOfBirth config = properties.getDateOfBirth();
    if (!config.isEnabled()) {
      return properties.getDisabledPlaceholder();
    }
    return config.getPlaceholder();
  }

  /**
   * Returns the configured placeholder generic structured-logging redaction
   * should substitute for a sensitive field's value.
   */
  public String loggingPlaceholder() {
    return properties.getLogging().getPlaceholder();
  }

  private String maskFirstLast(String value, MaskingProperties.EmailMobile config) {
    int n = value.length();
    String maskChar = config.getMaskCharacter();
    if (n <= 2) {
      int count = config.getMode() == MaskMode.FIXED_LENGTH ? config.getMaskLength() : n;
      return maskChar.repeat(Math.max(count, 1));
    }
    int hiddenCount =
        config.getMode() == MaskMode.FIXED_LENGTH ? config.getMaskLength() : n - 2;
    return value.charAt(0) + maskChar.repeat(Math.max(hiddenCount, 0)) + value.charAt(n - 1);
  }
}
