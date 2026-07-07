package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RedisCaptchaChallengeJsonMapper {

    private final ObjectMapper objectMapper;

    public RedisCaptchaChallengeJsonMapper() {
        this(new ObjectMapper());
    }

    RedisCaptchaChallengeJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(CaptchaChallenge challenge) {
        if (challenge == null) {
            throw new IllegalArgumentException("Captcha challenge must not be null");
        }
        try {
            return objectMapper.writeValueAsString(RedisCaptchaChallengeRecord.from(challenge));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Captcha challenge state serialization failed", exception);
        }
    }

    public CaptchaChallenge fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Captcha challenge JSON must not be blank");
        }
        try {
            return objectMapper.readValue(json, RedisCaptchaChallengeRecord.class).toCaptchaChallenge();
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Captcha challenge state deserialization failed", exception);
        }
    }
}