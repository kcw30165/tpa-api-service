package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RedisAuthFlowSessionJsonMapper {

    private final ObjectMapper objectMapper;

    public RedisAuthFlowSessionJsonMapper() {
        this(new ObjectMapper());
    }

    RedisAuthFlowSessionJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(AuthFlowSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Auth flow session must not be null");
        }
        try {
            return objectMapper.writeValueAsString(RedisAuthFlowSessionRecord.from(session));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Auth flow session state serialization failed", exception);
        }
    }

    public AuthFlowSession fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Auth flow session JSON must not be blank");
        }
        try {
            return objectMapper.readValue(json, RedisAuthFlowSessionRecord.class).toAuthFlowSession();
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Auth flow session state deserialization failed", exception);
        }
    }
}
