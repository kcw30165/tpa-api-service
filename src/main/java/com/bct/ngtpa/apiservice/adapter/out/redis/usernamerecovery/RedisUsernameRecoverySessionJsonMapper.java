package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * JSON serialization boundary for the protected Username Recovery session.
 *
 * <p>Serializes only {@link RedisUsernameRecoverySessionRecord} minimal fields and
 * rebuilds the domain aggregate on load. Serialization and deserialization
 * failures are never swallowed: they are wrapped in a {@link CacheException}
 * with a generic safe message (no stored content is disclosed).
 */
public class RedisUsernameRecoverySessionJsonMapper {

    private final ObjectMapper objectMapper;

    public RedisUsernameRecoverySessionJsonMapper() {
        this(new ObjectMapper());
    }

    RedisUsernameRecoverySessionJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String toJson(UsernameRecoverySession session) {
        Objects.requireNonNull(session, "session must not be null");
        try {
            return objectMapper.writeValueAsString(RedisUsernameRecoverySessionRecord.from(session));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Username recovery session state serialization failed", exception);
        }
    }

    public UsernameRecoverySession fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new CacheException("Username recovery session state deserialization failed");
        }
        try {
            return objectMapper.readValue(json, RedisUsernameRecoverySessionRecord.class).toSession();
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Username recovery session state deserialization failed", exception);
        }
    }
}
