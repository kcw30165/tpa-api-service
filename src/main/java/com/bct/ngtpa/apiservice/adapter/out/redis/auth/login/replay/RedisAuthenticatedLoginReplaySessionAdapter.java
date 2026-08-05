package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginReplaySessionPort;
import java.util.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

public final class RedisAuthenticatedLoginReplaySessionAdapter implements AuthenticatedLoginReplaySessionPort {
    private static final String CAPABILITY = "auth:authenticated-session";
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then redis.call('psetex',KEYS[1],ARGV[3],ARGV[2]); return 1 else return 0 end",
            Long.class);
    private final ReactiveStringRedisTemplate redis;
    private final RedisCacheKeyFactory keys;
    private final RedisAuthenticatedSessionJsonMapper json;

    public RedisAuthenticatedLoginReplaySessionAdapter(ReactiveStringRedisTemplate redis,
            RedisCacheKeyFactory keys, RedisAuthenticatedSessionJsonMapper json) {
        this.redis = Objects.requireNonNull(redis);
        this.keys = Objects.requireNonNull(keys);
        this.json = Objects.requireNonNull(json);
    }

    public Mono<Optional<ProtectedAuthenticatedSession>> loadForReplay(LoadAuthenticatedSessionForReplayCommand c) {
        return redis.opsForValue().get(key(c.authenticatedSessionId())).map(v -> Optional.of(json.fromJson(v)))
                .defaultIfEmpty(Optional.empty()).onErrorReturn(Optional.empty());
    }

    public Mono<AuthenticatedSessionCsrfRotationResult> rotateCsrfHashIfCurrent(
            RotateAuthenticatedSessionCsrfHashCommand c) {
        return redis.execute(ROTATE, List.of(key(c.authenticatedSessionId())),
                        List.of(json.toJson(c.expectedProtectedSession()), json.toJson(c.replacementProtectedSession()),
                                Long.toString(c.ttl().toMillis())))
                .next().map(v -> new AuthenticatedSessionCsrfRotationResult(v != null && v > 0
                        ? AuthenticatedSessionCsrfRotationStatus.ROTATED
                        : AuthenticatedSessionCsrfRotationStatus.CONFLICT))
                .defaultIfEmpty(new AuthenticatedSessionCsrfRotationResult(
                        AuthenticatedSessionCsrfRotationStatus.SYSTEM_FAILURE))
                .onErrorReturn(new AuthenticatedSessionCsrfRotationResult(
                        AuthenticatedSessionCsrfRotationStatus.SYSTEM_FAILURE));
    }

    private String key(AuthenticatedSessionId id) { return keys.key(CAPABILITY, id.value()); }
}
