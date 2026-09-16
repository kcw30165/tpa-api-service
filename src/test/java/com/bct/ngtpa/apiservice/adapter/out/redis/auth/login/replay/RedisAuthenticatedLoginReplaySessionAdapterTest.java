package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionForReplayCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.RotateAuthenticatedSessionCsrfHashCommand;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisAuthenticatedLoginReplaySessionAdapter}. Uses the
 * confirmed {@code ArgumentMatchers.<Object>anyList()} overload-disambiguation technique (rounds
 * 91/101) for {@code execute(...)}.
 */
@SuppressWarnings("unchecked")
class RedisAuthenticatedLoginReplaySessionAdapterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
  private final RedisCacheKeyFactory keys = mock(RedisCacheKeyFactory.class);
  private final RedisAuthenticatedSessionJsonMapper json = mock(RedisAuthenticatedSessionJsonMapper.class);

  private final AuthenticatedSessionId sid = new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private RedisAuthenticatedLoginReplaySessionAdapter newAdapter() {
    return new RedisAuthenticatedLoginReplaySessionAdapter(redis, keys, json);
  }

  private void givenKeyFactoryReady() {
    when(keys.key(ArgumentMatchers.eq("auth:authenticated-session"), any())).thenReturn("redis-key-1");
  }

  // ----- loadForReplay() -------------------------------------------------------

  @Test
  void loadForReplayMissingYieldsEmpty() {
    givenKeyFactoryReady();
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(
            newAdapter().loadForReplay(new LoadAuthenticatedSessionForReplayCommand(sid, "req-1")))
        .expectNext(java.util.Optional.empty())
        .verifyComplete();
  }

  @Test
  void loadForReplayFoundReturnsSession() {
    givenKeyFactoryReady();
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(any())).thenReturn(Mono.just("{}"));
    ProtectedAuthenticatedSession session = mock(ProtectedAuthenticatedSession.class);
    when(json.fromJson(any())).thenReturn(session);
    StepVerifier.create(
            newAdapter().loadForReplay(new LoadAuthenticatedSessionForReplayCommand(sid, "req-1")))
        .assertNext(optional -> assertThat(optional).contains(session))
        .verifyComplete();
  }

  @Test
  void loadForReplayRedisErrorYieldsEmpty() {
    givenKeyFactoryReady();
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(
            newAdapter().loadForReplay(new LoadAuthenticatedSessionForReplayCommand(sid, "req-1")))
        .expectNext(java.util.Optional.empty())
        .verifyComplete();
  }

  // ----- rotateCsrfHashIfCurrent() ---------------------------------------------

  private RotateAuthenticatedSessionCsrfHashCommand rotateCommand() {
    return new RotateAuthenticatedSessionCsrfHashCommand(
        sid, mock(ProtectedAuthenticatedSession.class), mock(ProtectedAuthenticatedSession.class),
        "raw-csrf-token", "hashed-csrf-token", Duration.ofSeconds(1800), "req-1");
  }

  @Test
  void rotateSucceedsWhenScriptReturnsPositive() {
    givenKeyFactoryReady();
    when(json.toJson(any())).thenReturn("{}");
    when(redis.execute(
            org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenReturn(Flux.just(1L));
    StepVerifier.create(newAdapter().rotateCsrfHashIfCurrent(rotateCommand()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionCsrfRotationStatus.ROTATED))
        .verifyComplete();
  }

  @Test
  void rotateYieldsConflictWhenScriptReturnsZero() {
    givenKeyFactoryReady();
    when(json.toJson(any())).thenReturn("{}");
    when(redis.execute(
            org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenReturn(Flux.just(0L));
    StepVerifier.create(newAdapter().rotateCsrfHashIfCurrent(rotateCommand()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionCsrfRotationStatus.CONFLICT))
        .verifyComplete();
  }

  @Test
  void rotateYieldsSystemFailureWhenScriptReturnsEmpty() {
    givenKeyFactoryReady();
    when(json.toJson(any())).thenReturn("{}");
    when(redis.execute(
            org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenReturn(Flux.empty());
    StepVerifier.create(newAdapter().rotateCsrfHashIfCurrent(rotateCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionCsrfRotationStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void rotateRedisErrorYieldsSystemFailure() {
    givenKeyFactoryReady();
    when(json.toJson(any())).thenReturn("{}");
    when(redis.execute(
            org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenReturn(Flux.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().rotateCsrfHashIfCurrent(rotateCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionCsrfRotationStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }
}
