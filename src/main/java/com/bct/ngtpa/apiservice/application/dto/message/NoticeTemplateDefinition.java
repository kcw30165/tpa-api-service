package com.bct.ngtpa.apiservice.application.dto.message;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
    if (channel == MessageDeliveryChannel.EMAIL && (subject == null || subject.isBlank())) {
      throw new IllegalArgumentException("subject must not be blank for EMAIL channel");
    }
    requiredPlaceholders =
        requiredPlaceholders == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(requiredPlaceholders));
  }
}
