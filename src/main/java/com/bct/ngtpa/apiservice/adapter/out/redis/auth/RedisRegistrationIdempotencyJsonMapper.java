package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

public class RedisRegistrationIdempotencyJsonMapper {
    private final ObjectMapper objectMapper;

    public RedisRegistrationIdempotencyJsonMapper() {
        this(defaultObjectMapper());
    }

    public RedisRegistrationIdempotencyJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(RegistrationIdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(toRedisRecord(record));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize registration idempotency record");
        }
    }

    public RegistrationIdempotencyRecord fromJson(String json) {
        try {
            var redisRecord = objectMapper.readValue(json, RedisRegistrationIdempotencyRecord.class);
            return fromRedisRecord(redisRecord);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize registration idempotency record");
        }
    }

    private static RedisRegistrationIdempotencyRecord toRedisRecord(RegistrationIdempotencyRecord record) {
        RedisRegistrationIdempotencyResponseSnapshotRecord snapshot = null;
        if (record.responseSnapshot() != null) {
            snapshot = new RedisRegistrationIdempotencyResponseSnapshotRecord(
                    record.responseSnapshot().status(),
                    record.responseSnapshot().serializedBody(),
                    record.responseSnapshot().safeHeaders());
        }
        return new RedisRegistrationIdempotencyRecord(
                record.action().name(),
                record.idempotencyKey().value(),
                record.requestHash().value(),
                record.status().name(),
                snapshot,
                record.createdAt().toString(),
                record.updatedAt().toString(),
                record.expiresAt().toString());
    }

    private static RegistrationIdempotencyRecord fromRedisRecord(RedisRegistrationIdempotencyRecord redisRecord) {
        RegistrationIdempotencyResponseSnapshot snapshot = null;
        if (redisRecord.responseSnapshot() != null) {
            snapshot = new RegistrationIdempotencyResponseSnapshot(
                    redisRecord.responseSnapshot().status(),
                    redisRecord.responseSnapshot().serializedBody(),
                    redisRecord.responseSnapshot().safeHeaders() == null ? Map.of()
                            : redisRecord.responseSnapshot().safeHeaders());
        }
        return new RegistrationIdempotencyRecord(
                RegistrationIdempotencyAction.valueOf(redisRecord.action()),
                new RegistrationIdempotencyKey(redisRecord.idempotencyKey()),
                new RegistrationRequestHash(redisRecord.requestHash()),
                RegistrationIdempotencyStatus.valueOf(redisRecord.status()),
                snapshot,
                Instant.parse(redisRecord.createdAt()),
                Instant.parse(redisRecord.updatedAt()),
                Instant.parse(redisRecord.expiresAt()));
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
