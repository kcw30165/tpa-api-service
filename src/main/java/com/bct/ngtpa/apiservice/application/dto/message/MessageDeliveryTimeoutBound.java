package com.bct.ngtpa.apiservice.application.dto.message;

import java.util.Objects;

public record MessageDeliveryTimeoutBound(MessageDeliveryChannel channel, int timeoutMillis) {

  public MessageDeliveryTimeoutBound {
    Objects.requireNonNull(channel, "channel must not be null");
    if (timeoutMillis <= 0) {
      throw new IllegalArgumentException("message delivery timeout must be positive");
    }
  }
}
