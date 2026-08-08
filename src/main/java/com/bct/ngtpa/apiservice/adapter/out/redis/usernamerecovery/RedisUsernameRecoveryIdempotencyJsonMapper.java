package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * JSON serialization boundary for the Username Recovery idempotency record.
 *
 * <p>Serializes only {@link RedisUsernameRecoveryIdempotencyRecord} minimal fields and
 * rebuilds the application record on load. Serialization and deserialization failures
 * are never swallowed: they are wrapped in a {@link CacheException} with a generic safe
 * message (no stored content is disclosed).
 */
public class RedisUsernameRecoveryIdempotencyJsonMapper {

    private final ObjectMapper objectMapper;

    public RedisUsernameRecoveryIdempotencyJsonMapper() {
        this(new ObjectMapper());
    }

    RedisUsernameRecoveryIdempotencyJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String toJson(UsernameRecoveryIdempotencyRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        try {
            return objectMapper.writeValueAsString(RedisUsernameRecoveryIdempotencyRecord.from(record));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException(
                    "Username recovery idempotency state serialization failed", exception);
        }
    }

    public UsernameRecoveryIdempotencyRecord fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new CacheException("Username recovery idempotency state deserialization failed");
        }
        try {
            return objectMapper.readValue(json, RedisUsernameRecoveryIdempotencyRecord.class).toRecord();
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException(
                    "Username recovery idempotency state deserialization failed", exception);
        }
    }
}
