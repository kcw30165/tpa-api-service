package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisAuthFlowSessionPersistenceCompatibilityAdapter}.
 *
 * <p>Note (round 112/113/114): {@code establish()}'s {@code .switchIfEmpty(initialize())} passes
 * an ALREADY-INVOKED {@code Mono} (a plain method call, not {@code Mono.defer(...)}), so {@code
 * initialize()}'s body - including {@code redis.opsForValue().setIfAbsent(...)} - executes EAGERLY
 * on every single call to {@code establish()}, regardless of which branch is ultimately taken.
 * {@link #newAdapter()} therefore ALWAYS stubs {@code setIfAbsent(...)} with a safe default of
 * {@code true}; the two "concurrent conflict" tests below explicitly override this to {@code
 * false} to exercise the "someone else won the race" branches.
 */
@SuppressWarnings("unchecked")
class RedisAuthFlowSessionPersistenceCompatibilityAdapterTest {

  private static final int SUPPORTED_VERSION =
      RedisAuthFlowSessionPersistenceCompatibilityAdapter.SUPPORTED_VERSION;

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
  private final RedisCacheKeyFactory keys = mock(RedisCacheKeyFactory.class);

  private RedisAuthFlowSessionPersistenceCompatibilityAdapter newAdapter() {
    when(keys.key(any(), any())).thenReturn("redis-key-1");
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), any())).thenReturn(Mono.just(true));
    return new RedisAuthFlowSessionPersistenceCompatibilityAdapter(redis, keys);
  }

  // ----- establish(): unsupported configured version --------------------------

  @Test
  void unsupportedConfiguredVersionErrors() {
    StepVerifier.create(newAdapter().establish(SUPPORTED_VERSION + 1))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(
                        AuthFlowSessionPersistenceCompatibilityStatus.CONFIGURATION_VERSION_UNSUPPORTED))
        .verifyComplete();
  }

  // ----- establish(): existing marker evaluation --------------------------------

  @Test
  void existingCompatibleMarkerYieldsCompatible() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.just(Integer.toString(SUPPORTED_VERSION)));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.COMPATIBLE))
        .verifyComplete();
  }

  @Test
  void existingMismatchedMarkerYieldsExistingVersionMismatch() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.just(Integer.toString(SUPPORTED_VERSION + 5)));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.EXISTING_VERSION_MISMATCH))
        .verifyComplete();
  }

  @Test
  void existingBlankMarkerYieldsMarkerCorrupt() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.just("   "));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.MARKER_CORRUPT))
        .verifyComplete();
  }

  @Test
  void existingNonNumericMarkerYieldsMarkerCorrupt() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.just("not-a-number"));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.MARKER_CORRUPT))
        .verifyComplete();
  }

  // ----- establish(): initialization path (no existing marker) -----------------

  @Test
  void noExistingMarkerInitializesSuccessfully() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZED))
        .verifyComplete();
  }

  @Test
  void concurrentInitializationConflictThenReadsCompatibleMarker() {
    var adapter = newAdapter();
    when(values.get(any()))
        .thenReturn(Mono.empty())
        .thenReturn(Mono.just(Integer.toString(SUPPORTED_VERSION)));
    // Override newAdapter()'s default setIfAbsent(true) - THIS test needs
    // false to exercise the "someone else won the race" branch.
    when(values.setIfAbsent(any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.COMPATIBLE))
        .verifyComplete();
  }

  @Test
  void concurrentInitializationConflictThenStillNoMarkerYieldsInitializationConflict() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.empty()).thenReturn(Mono.empty());
    // Override newAdapter()'s default setIfAbsent(true) - THIS test needs
    // false to exercise the "someone else won the race, and marker STILL
    // absent on re-read" branch.
    when(values.setIfAbsent(any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZATION_CONFLICT))
        .verifyComplete();
  }

  // ----- establish(): storage unavailable ---------------------------------------

  @Test
  void redisErrorYieldsStorageUnavailable() {
    var adapter = newAdapter();
    when(values.get(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(adapter.establish(SUPPORTED_VERSION))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(AuthFlowSessionPersistenceCompatibilityStatus.STORAGE_UNAVAILABLE))
        .verifyComplete();
  }
}
