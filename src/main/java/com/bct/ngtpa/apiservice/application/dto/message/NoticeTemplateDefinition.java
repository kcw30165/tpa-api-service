package com.bct.ngtpa.apiservice.application.dto.message;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resolved content for a single notice (messageType), channel, and locale.
 *
 * <p>This record intentionally carries NO channel-conditional business validation (e.g. "subject
 * required for EMAIL"), to avoid violating the Open/Closed Principle: a future channel
 * (WhatsApp, WeChat, etc.) must never require modifying this class. This mirrors the real
 * precedent in {@code MessageDeliveryCommand}, whose compact constructor performs only a
 * defensive copy with zero channel-specific branching; channel-specific rules live in the port
 * implementation that consumes the data ({@code MessageDeliveryAdapter.validate()}), not in the
 * data type itself.
 *
 * <p>Safety is not weakened by this: the same "subject required for EMAIL" rule is already
 * enforced twice elsewhere - at config load time ({@code NoticeTemplateProperties.validate()})
 * and at dispatch time ({@code MessageDeliveryAdapter.validate()}, which runs on every {@code
 * MessageDeliveryCommand} built from a resolved {@code NoticeTemplateDefinition} before any
 * message is actually sent).
 */
public record NoticeTemplateDefinition(
    MessageDeliveryType messageType,
    MessageDeliveryChannel channel,
    String locale,
    String subject,
    String body,
    Set<String> requiredPlaceholders) {

  public NoticeTemplateDefinition {
    Objects.requireNonNull(messageType, "messageType must not be null");
    Objects.requireNonNull(channel, "channel must not be null");
    if (locale == null || locale.isBlank()) {
      throw new IllegalArgumentException("locale must not be blank");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("body must not be blank");
    }
    requiredPlaceholders =
        requiredPlaceholders == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(requiredPlaceholders));
  }
}
