package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import java.util.Objects;

/**
 * Feature-scoped Redis key helper for Username Recovery idempotency records.
 *
 * <p>Keys follow {@code <prefix>:username-recovery:idempotency:<action>:<opaque-key>}. The scope is
 * feature-specific and prefix-configured; the only key components derived from the operation are
 * the action key namespace and the opaque idempotency key value. Keys must never be logged or
 * exposed.
 */
final class RedisUsernameRecoveryIdempotencyKeyFactory {

  private static final String CAPABILITY = "username-recovery";
  private static final String IDEMPOTENCY_NAMESPACE = "idempotency";

  private RedisUsernameRecoveryIdempotencyKeyFactory() {}

  static String keyFor(
      RedisCacheKeyFactory keyFactory,
      UsernameRecoveryIdempotencyAction action,
      UsernameRecoveryIdempotencyKey idempotencyKey) {
    Objects.requireNonNull(keyFactory, "keyFactory must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    return keyFactory.key(
        CAPABILITY, IDEMPOTENCY_NAMESPACE, action.keyNamespace(), idempotencyKey.value());
  }
}
