package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import org.junit.jupiter.api.Test;

/**
 * Group 1: feature-scoped opaque Redis key scope for the Username Recovery idempotency records.
 *
 * <p>Keys follow {@code <prefix>:username-recovery:idempotency:<action>:<opaque-key>}. Only opaque
 * values are key components; keys must never contain raw sensitive values and are never logged or
 * exposed.
 */
class RedisUsernameRecoveryIdempotencyKeyFactoryTest {

  @Test
  void keyIsFeatureScopedAndFollowsExpectedFormat() {
    var key =
        RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
            new RedisCacheKeyFactory("ngtpa"),
            UsernameRecoveryIdempotencyAction.REQUEST,
            new UsernameRecoveryIdempotencyKey("idem_12345678"));

    assertThat(key)
        .isEqualTo("ngtpa:username-recovery:idempotency:username-recovery-request:idem_12345678");
  }

  @Test
  void keyScopesByActionNamespace() {
    var key =
        RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
            new RedisCacheKeyFactory("ngtpa"),
            UsernameRecoveryIdempotencyAction.START,
            new UsernameRecoveryIdempotencyKey("idem_12345678"));

    assertThat(key)
        .isEqualTo("ngtpa:username-recovery:idempotency:username-recovery-start:idem_12345678");
  }

  @Test
  void keyNeverContainsRawSensitiveValues() {
    var key =
        RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
            new RedisCacheKeyFactory("ngtpa"),
            UsernameRecoveryIdempotencyAction.REQUEST,
            new UsernameRecoveryIdempotencyKey("idem_12345678"));

    assertThat(key)
        .doesNotContain("member@example.com")
        .doesNotContain("user-sch")
        .doesNotContain("csrf")
        .doesNotContain("cookie")
        .doesNotContain("policy")
        .doesNotContain("cert")
        .doesNotContain("1990-01-01");
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(
            () ->
                RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
                    null,
                    UsernameRecoveryIdempotencyAction.REQUEST,
                    new UsernameRecoveryIdempotencyKey("idem_12345678")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
                    new RedisCacheKeyFactory("ngtpa"),
                    null,
                    new UsernameRecoveryIdempotencyKey("idem_12345678")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
                    new RedisCacheKeyFactory("ngtpa"),
                    UsernameRecoveryIdempotencyAction.REQUEST,
                    null))
        .isInstanceOf(NullPointerException.class);
  }
}
