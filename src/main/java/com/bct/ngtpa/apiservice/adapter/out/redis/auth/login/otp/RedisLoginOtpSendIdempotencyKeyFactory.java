package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp;

public final class RedisLoginOtpSendIdempotencyKeyFactory {
  private final String prefix;

  public RedisLoginOtpSendIdempotencyKeyFactory(String prefix) {
    if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("prefix required");
    this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
  }

  public String keyFor(
      com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyAction action,
      String opaqueIdempotencyKey) {
    if (opaqueIdempotencyKey == null || !opaqueIdempotencyKey.matches("^[A-Za-z0-9_-]{8,128}$"))
      throw new IllegalArgumentException("invalid opaque idempotency key");
    return prefix + ":auth:idempotency:login:" + action.keyNamespace() + ":" + opaqueIdempotencyKey;
  }
}
