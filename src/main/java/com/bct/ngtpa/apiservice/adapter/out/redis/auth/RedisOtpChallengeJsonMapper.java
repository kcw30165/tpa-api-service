package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RedisOtpChallengeJsonMapper {

    private final ObjectMapper objectMapper;

    public RedisOtpChallengeJsonMapper() {
        this(new ObjectMapper());
    }

    RedisOtpChallengeJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(OtpChallenge challenge) {
        if (challenge == null) {
            throw new IllegalArgumentException("OTP challenge must not be null");
        }
        try {
            return objectMapper.writeValueAsString(RedisOtpChallengeRecord.from(challenge));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("OTP challenge state serialization failed", exception);
        }
    }

    public OtpChallenge fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("OTP challenge JSON must not be blank");
        }
        try {
            return objectMapper.readValue(json, RedisOtpChallengeRecord.class).toOtpChallenge();
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("OTP challenge state deserialization failed", exception);
        }
    }
}