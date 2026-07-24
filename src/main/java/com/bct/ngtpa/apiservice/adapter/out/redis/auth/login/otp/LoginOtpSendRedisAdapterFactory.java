package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp;

import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSendIdempotencyPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

public final class LoginOtpSendRedisAdapterFactory {
    private LoginOtpSendRedisAdapterFactory() {
    }

    public static LoginOtpSendIdempotencyPort create(RedisLoginOtpSendIdempotencyKeyFactory keys,
            ReactiveStringRedisTemplate redis) {
        return new RedisLoginOtpSendIdempotencyAdapter(keys, new ReactiveLoginOtpSendRedisOperations(redis));
    }
}
