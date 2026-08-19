package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;

public final class RedisLoginIdempotencyKeyFactory {
  private final String prefix;

  public RedisLoginIdempotencyKeyFactory(String prefix) {
    if (prefix == null || prefix.isBlank())
      throw new IllegalArgumentException("prefix must not be blank");
    this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }

  public String keyFor(LoginIdempotencyAction action, LoginIdempotencyPersistenceId id) {
    if (action == null || id == null)
      throw new IllegalArgumentException("action and persistence identity required");
    return prefix + ":auth:idempotency:login:" + action.keyNamespace() + ":" + id.value();
  }
}
