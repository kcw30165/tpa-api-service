package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RedisUsernameRecoveryWorkflowStateKeyFactoryTest {

    @Test
    void buildsWorkflowStateKeyWithExactShape() {
        var keyFactory = new RedisCacheKeyFactory("ngtpa");
        var key = RedisUsernameRecoveryWorkflowStateKeyFactory.keyFor(
                keyFactory, new AuthFlowSessionId("afs_abc123"));

        assertThat(key).isEqualTo("ngtpa:username-recovery:workflow-state:afs_abc123");
        assertThat(key)
                .doesNotContain("member@example.com")
                .doesNotContain("user-sch")
                .doesNotContain("csrf")
                .doesNotContain("email")
                .doesNotContain("auth_flow_sid");
    }

    @Test
    void keyUsesCanonicalAuthFlowSessionIdInput() {
        var keyFactory = new RedisCacheKeyFactory("ngtpa");
        var key = RedisUsernameRecoveryWorkflowStateKeyFactory.keyFor(
                keyFactory, new AuthFlowSessionId("afs_canonical_1"));

        assertThat(key).isEqualTo("ngtpa:username-recovery:workflow-state:afs_canonical_1");
        assertThat(key).doesNotContain("authFlowSessionId");
    }

    @Test
    void nullDependencyAndNullIdAreRejected() {
        var keyFactory = new RedisCacheKeyFactory("ngtpa");

        assertThatThrownBy(() -> RedisUsernameRecoveryWorkflowStateKeyFactory.keyFor(
                null, new AuthFlowSessionId("afs_abc123")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RedisUsernameRecoveryWorkflowStateKeyFactory.keyFor(
                keyFactory, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void keyFactoryExposesNoTtlApi() {
        var methodNames = Arrays.stream(
                        RedisUsernameRecoveryWorkflowStateKeyFactory.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(methodNames)
                .doesNotContain("ttl", "expire", "withTtl", "setTtl");
    }
}