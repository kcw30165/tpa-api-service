package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSendIdempotencyPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class LoginOtpSendIdempotencyConfig {
    @Bean
    RedisLoginOtpSendIdempotencyKeyFactory redisLoginOtpSendIdempotencyKeyFactory(
            @Value("${redis-cache.key-prefix:ngtpa}") String prefix) {
        return new RedisLoginOtpSendIdempotencyKeyFactory(prefix);
    }

    @Bean
    LoginOtpSendIdempotencyPort loginOtpSendIdempotencyPort(RedisLoginOtpSendIdempotencyKeyFactory keys,
            ReactiveStringRedisTemplate redis, AuthWorkflowProperties properties) {
        properties.validateLoginPolicy();
        int ttl = properties.getLogin().getIdempotency().getOtpSendTtlSeconds();
        Duration.ofSeconds(ttl);
        return LoginOtpSendRedisAdapterFactory.create(keys, redis);
    }
}
