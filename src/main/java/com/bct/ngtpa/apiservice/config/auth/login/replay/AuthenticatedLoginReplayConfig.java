package com.bct.ngtpa.apiservice.config.auth.login.replay;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay.RedisAuthenticatedLoginReplaySessionAdapter;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class AuthenticatedLoginReplayConfig {
    @Bean
    AuthenticatedLoginReplaySessionPort authenticatedLoginReplaySessionPort(
            ReactiveStringRedisTemplate redis, RedisCacheKeyFactory keys,
            RedisAuthenticatedSessionJsonMapper json) {
        return new RedisAuthenticatedLoginReplaySessionAdapter(redis, keys, json);
    }

    @Bean
    RecoverCommittedLoginUseCase recoverCommittedLoginUseCase(
            AuthenticatedLoginHandoffPort handoffs, AuthenticatedLoginReplaySessionPort replaySessions,
            AuthenticatedSessionProtectorPort protector, CsrfTokenGenerator csrfTokens,
            CsrfTokenHasher csrfHasher, Clock clock) {
        return new RecoverCommittedLoginService(handoffs, replaySessions, protector, csrfTokens, csrfHasher, clock);
    }
}
