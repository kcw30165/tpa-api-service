package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import java.util.Objects;

/** Builds non-disclosing shared idempotency keys from a keyed persistence locator. */
public final class RedisIdempotencyKeyFactory {
  private static final String CAPABILITY = "idempotency";
  private static final String VERSION = "v1";
  private final RedisCacheKeyFactory keys;
  private final IdempotencyPersistenceIdDeriver persistenceIds;

  public RedisIdempotencyKeyFactory(
      RedisCacheKeyFactory keys, IdempotencyPersistenceIdDeriver persistenceIds) {
    this.keys = Objects.requireNonNull(keys, "Redis key factory must not be null");
    this.persistenceIds =
        Objects.requireNonNull(
            persistenceIds, "idempotency persistence ID deriver must not be null");
  }

  public String keyFor(IdempotencyOperationId operationId) {
    Objects.requireNonNull(operationId, "operation id must not be null");
    return keys.key(CAPABILITY, VERSION, persistenceIds.derive(operationId));
  }
}
