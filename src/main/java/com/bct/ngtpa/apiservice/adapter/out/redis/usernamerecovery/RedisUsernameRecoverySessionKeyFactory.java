package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.util.Objects;

/**
 * Feature-scoped Redis key helper for protected Username Recovery sessions.
 *
 * <p>Keys follow {@code <prefix>:username-recovery:session:<opaque-session-id>}.
 * The scope is feature-specific and prefix-configured; the only key component
 * derived from the aggregate is the opaque session id. Keys must never be
 * logged or exposed.
 */
final class RedisUsernameRecoverySessionKeyFactory {

    private static final String CAPABILITY = "username-recovery";
    private static final String SESSION_NAMESPACE = "session";

    private RedisUsernameRecoverySessionKeyFactory() {
    }

    static String keyFor(RedisCacheKeyFactory keyFactory, UsernameRecoverySessionId sessionId) {
        Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return keyFactory.key(CAPABILITY, SESSION_NAMESPACE, sessionId.value());
    }
}
