package com.bct.ngtpa.apiservice.config.auth.session;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthenticatedSessionProtectionProperties.class)
public class AuthenticatedSessionConfig {
    @Bean
    RedisAuthenticatedSessionJsonMapper redisAuthenticatedSessionJsonMapper() {
        return new RedisAuthenticatedSessionJsonMapper();
    }

    @Bean
    AuthenticatedSessionPort authenticatedSessionPort(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthenticatedSessionJsonMapper jsonMapper) {
        return new RedisAuthenticatedSessionAdapter(cachePort, keyFactory, jsonMapper);
    }

    @Bean
    AuthenticatedSessionProtectorPort authenticatedSessionProtector(
            AuthenticatedSessionProtectionProperties properties,
            ObjectMapper objectMapper) {
        properties.validate();
        return new AesGcmAuthenticatedSessionProtector(
                properties.getBase64Key(),
                objectMapper.copy());
    }
}
