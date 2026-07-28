package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Redis JSON boundary with explicit ISO-8601 lifecycle timestamp encoding. */
public final class RedisAuthenticatedSessionJsonMapper {
    private final ObjectMapper mapper;

    public RedisAuthenticatedSessionJsonMapper() {
        this(new ObjectMapper());
    }

    public RedisAuthenticatedSessionJsonMapper(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public String toJson(ProtectedAuthenticatedSession value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            return mapper.writeValueAsString(WireEnvelope.from(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize protected authenticated session");
        }
    }

    public ProtectedAuthenticatedSession fromJson(String value) {
        try {
            return mapper.readValue(value, WireEnvelope.class).toDomain();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize protected authenticated session");
        }
    }

    private record WireEnvelope(
            String authenticatedSessionId,
            String status,
            WireData data,
            String createdAt,
            String updatedAt,
            String idleExpiresAt,
            String absoluteExpiresAt) {

        static WireEnvelope from(ProtectedAuthenticatedSession value) {
            return new WireEnvelope(
                    value.authenticatedSessionId(),
                    value.status().name(),
                    new WireData(value.data().attributes()),
                    value.createdAt().toString(),
                    value.updatedAt().toString(),
                    value.idleExpiresAt().toString(),
                    value.absoluteExpiresAt().toString());
        }

        ProtectedAuthenticatedSession toDomain() {
            return new ProtectedAuthenticatedSession(
                    authenticatedSessionId,
                    AuthenticatedSessionStatus.valueOf(status),
                    new ProtectedAuthenticatedSession.Data(
                            data == null || data.attributes() == null
                                    ? Map.of()
                                    : data.attributes()),
                    Instant.parse(createdAt),
                    Instant.parse(updatedAt),
                    Instant.parse(idleExpiresAt),
                    Instant.parse(absoluteExpiresAt));
        }
    }

    private record WireData(Map<String, String> attributes) {
    }
}
