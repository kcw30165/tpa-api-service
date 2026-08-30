package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitBucket;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedisRateLimitAdapterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  private ReactiveStringRedisTemplate redisTemplate;
  private RedisRateLimitAdapter adapter;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(ReactiveStringRedisTemplate.class);
    adapter =
        new RedisRateLimitAdapter(redisTemplate, new RedisCacheKeyFactory("ngtpa"), FIXED_CLOCK);
  }

  @Test
  void disabledPolicyReturnsDisabledWithoutRedisCall() {
    var command =
        new RateLimitCommand(
            RateLimitBucket.REGISTRATION_START_BY_IP, null, false, 0, 0, "request-123");

    StepVerifier.create(adapter.checkAndConsume(command))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(RateLimitStatus.DISABLED);
              assertThat(result.bucket()).isEqualTo(RateLimitBucket.REGISTRATION_START_BY_IP);
              assertThat(result.limit()).isZero();
              assertThat(result.remaining()).isZero();
              assertThat(result.retryAfterSeconds()).isZero();
              assertThat(result.resetAt()).isNull();
            })
        .verifyComplete();

    verifyNoInteractions(redisTemplate);
  }

  @Test
  void buildsRateLimitKeyWithAuthRateNamespace() {
    String expectedKey = "ngtpa:auth:rate:OTP_SEND_BY_DESTINATION_HASH:bucket_abc123";
    doReturn(Flux.just(List.of(1L, 600L)))
        .when(redisTemplate)
        .execute(any(RedisScript.class), eq(List.of(expectedKey)), eq(List.of("600")));

    StepVerifier.create(adapter.checkAndConsume(enabledCommand()))
        .expectNextMatches(result -> result.status() == RateLimitStatus.ALLOWED)
        .verifyComplete();

    verify(redisTemplate)
        .execute(any(RedisScript.class), eq(List.of(expectedKey)), eq(List.of("600")));
    assertThat(expectedKey)
        .isNotEqualTo("ngtpa:rate:bucket_abc123")
        .doesNotContain("member@example.com")
        .doesNotContain("91234567");
  }

  @Test
  void allowsWhenCounterWithinLimit() {
    stubScriptResult(1L, 600L);

    StepVerifier.create(adapter.checkAndConsume(enabledCommand()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(RateLimitStatus.ALLOWED);
              assertThat(result.limit()).isEqualTo(3);
              assertThat(result.remaining()).isEqualTo(2);
              assertThat(result.retryAfterSeconds()).isZero();
              assertThat(result.resetAt()).isEqualTo(Instant.parse("2026-01-01T00:10:00Z"));
            })
        .verifyComplete();
  }

  @Test
  void blocksWhenCounterExceedsLimit() {
    stubScriptResult(4L, 500L);

    StepVerifier.create(adapter.checkAndConsume(enabledCommand()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(RateLimitStatus.BLOCKED);
              assertThat(result.limit()).isEqualTo(3);
              assertThat(result.remaining()).isZero();
              assertThat(result.retryAfterSeconds()).isEqualTo(500);
              assertThat(result.resetAt()).isEqualTo(Instant.parse("2026-01-01T00:08:20Z"));
            })
        .verifyComplete();
  }

  @Test
  void setsExpiryOnlyOnFirstAttempt() {
    stubScriptResult(1L, 600L);

    StepVerifier.create(adapter.checkAndConsume(enabledCommand()))
        .expectNextMatches(result -> result.status() == RateLimitStatus.ALLOWED)
        .verifyComplete();

    ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(scriptCaptor.capture(), eq(List.of(redisKey())), eq(List.of("600")));
    assertThat(scriptCaptor.getValue().getScriptAsString())
        .contains("redis.call('INCR', KEYS[1])")
        .contains("if current == 1 then")
        .contains("redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))");
  }

  @Test
  void doesNotResetTtlOnSubsequentAttempts() {
    assertThat(RedisRateLimitAdapter.INCREMENT_AND_TTL_SCRIPT_TEXT)
        .contains("if current == 1 then")
        .doesNotContain("PEXPIRE")
        .contains("local ttl = redis.call('TTL', KEYS[1])");
  }

  @Test
  void fallsBackToWindowSecondsWhenRedisTtlUnavailable() {
    stubScriptResult(4L, -1L);

    StepVerifier.create(adapter.checkAndConsume(enabledCommand()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(RateLimitStatus.BLOCKED);
              assertThat(result.retryAfterSeconds()).isEqualTo(600);
              assertThat(result.resetAt()).isEqualTo(Instant.parse("2026-01-01T00:10:00Z"));
            })
        .verifyComplete();
  }

  @Test
  void rejectsInvalidEnabledCommand() {
    StepVerifier.create(adapter.checkAndConsume(null))
        .expectError(IllegalArgumentException.class)
        .verify();

    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> new RateLimitCommand(null, "opaque", true, 1, 1, null));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitCommand(RateLimitBucket.REGISTRATION_START_BY_IP, " ", true, 1, 1, null));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitCommand(
                RateLimitBucket.REGISTRATION_START_BY_IP, "opaque", true, 0, 1, null));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitCommand(
                RateLimitBucket.REGISTRATION_START_BY_IP, "opaque", true, 1, 0, null));

    verifyNoInteractions(redisTemplate);
  }

  @Test
  void redisFailurePropagatesSafely() {
    doReturn(
            Flux.error(
                new RuntimeException(
                    "redis failed for "
                        + redisKey()
                        + " member@example.com 91234567 OTP CSRF cookie")))
        .when(redisTemplate)
        .execute(any(RedisScript.class), eq(List.of(redisKey())), eq(List.of("600")));

    StepVerifier.create(adapter.checkAndConsume(enabledCommand()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(CacheException.class);
              assertThat(error.getMessage())
                  .doesNotContain("bucket_abc123")
                  .doesNotContain(redisKey())
                  .doesNotContain("member@example.com")
                  .doesNotContain("91234567")
                  .doesNotContain("OTP")
                  .doesNotContain("CSRF")
                  .doesNotContain("cookie");
            })
        .verify();
  }

  private void stubScriptResult(long count, long ttl) {
    doReturn(Flux.just(List.of(count, ttl)))
        .when(redisTemplate)
        .execute(any(RedisScript.class), eq(List.of(redisKey())), eq(List.of("600")));
  }

  private String redisKey() {
    return "ngtpa:auth:rate:OTP_SEND_BY_DESTINATION_HASH:bucket_abc123";
  }

  private RateLimitCommand enabledCommand() {
    return new RateLimitCommand(
        RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH, "bucket_abc123", true, 3, 600, "request-123");
  }
}
