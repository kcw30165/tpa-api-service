package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

/**
 * Feature-scoped Redis key helper for the protected Username Recovery workflow-state sidecar.
 *
 * <p>Keys follow {@code <prefix>:username-recovery:workflow-state:<opaque-auth-flow-session-id>}.
 * The scope is feature-specific and prefix-configured; the only key component derived from the
 * aggregate is the opaque auth-flow session id. The helper owns no TTL behavior, exposes no full
 * key through {@code toString()} or exception messages, and keys must never be logged or exposed.
 */
final class RedisUsernameRecoveryWorkflowStateKeyFactory {

    private static final String CAPABILITY = "username-recovery";
    private static final String WORKFLOW_STATE_NAMESPACE = "workflow-state";

    private RedisUsernameRecoveryWorkflowStateKeyFactory() {
    }

    static String keyFor(RedisCacheKeyFactory keyFactory, AuthFlowSessionId authFlowSessionId) {
        Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        return keyFactory.key(CAPABILITY, WORKFLOW_STATE_NAMESPACE, authFlowSessionId.value());
    }
}