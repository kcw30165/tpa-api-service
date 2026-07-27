package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public final class RedisAuthenticatedSessionJsonMapper {
    private final ObjectMapper mapper;

    public RedisAuthenticatedSessionJsonMapper() {
        this(new ObjectMapper());
    }

    public RedisAuthenticatedSessionJsonMapper(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public String toJson(ProtectedAuthenticatedSession value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize protected authenticated session");
        }
    }

    public ProtectedAuthenticatedSession fromJson(String value) {
        try {
            return mapper.readValue(value, ProtectedAuthenticatedSession.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize protected authenticated session");
        }
    }
}
