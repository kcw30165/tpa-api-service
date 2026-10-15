package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionForReplayCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RedisAuthenticatedLoginReplaySessionAdapter}. */
@SuppressWarnings("unchecked")
class RedisAuthenticatedLoginReplaySessionAdapterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values =
      mock(ReactiveValueOperations.class);
  private final RedisCacheKeyFactory keys = mock(RedisCacheKeyFactory.class);
  private final RedisAuthenticatedSessionJsonMapper json =
      mock(RedisAuthenticatedSessionJsonMapper.class);

  private final AuthenticatedSessionId sid =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private RedisAuthenticatedLoginReplaySessionAdapter newAdapter() {
    return new RedisAuthenticatedLoginReplaySessionAdapter(redis, keys, json);
  }

  private void givenKeyFactoryReady() {
    when(keys.key(ArgumentMatchers.eq("auth:authenticated-session"), any()))
        .thenReturn("redis-key-1");
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
}
