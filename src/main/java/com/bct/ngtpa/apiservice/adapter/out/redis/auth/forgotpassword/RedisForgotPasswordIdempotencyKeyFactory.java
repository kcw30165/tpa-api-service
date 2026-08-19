package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import java.util.Objects;

public final class RedisForgotPasswordIdempotencyKeyFactory {
  private final String prefix;

  public RedisForgotPasswordIdempotencyKeyFactory(String prefix) {
    Objects.requireNonNull(prefix, "prefix must not be null");
    if (prefix.isBlank()) {
      throw new IllegalArgumentException("prefix must not be blank");
    }
    this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }

  public String keyFor(
      ForgotPasswordIdempotencyAction action, ForgotPasswordIdempotencyKey idempotencyKey) {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    return prefix + ":auth:idempotency:" + action.keyNamespace() + ":" + idempotencyKey.value();
  }
}
