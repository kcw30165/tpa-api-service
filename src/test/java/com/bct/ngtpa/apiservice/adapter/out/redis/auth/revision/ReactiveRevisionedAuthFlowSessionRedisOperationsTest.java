package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ReactiveRevisionedAuthFlowSessionRedisOperations}.
 * Mocks {@link ReactiveStringRedisTemplate} directly (single confirmed dependency, round 96).
 * Uses {@link ArgumentMatchers#anyList()} with an explicit {@code <Object>} type witness for the
 * script-args stub to force compile-time binding to the {@code List<?>} overload of {@code
 * execute(...)} - the production code always packs multiple Lua script arguments into a single
 * {@code List.of(...)}, which only makes semantic sense (as separate {@code ARGV[1]}/{@code
 * ARGV[2]} entries) if it binds to that overload rather than varargs (which would instead wrap
 * the whole list as one opaque argument).
 */
@SuppressWarnings("unchecked")
class ReactiveRevisionedAuthFlowSessionRedisOperationsTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);

  private ReactiveRevisionedAuthFlowSessionRedisOperations newOperations() {
    return new ReactiveRevisionedAuthFlowSessionRedisOperations(redis);
  }

  private void givenExecuteReturns(Flux<Long> result) {
    when(redis.execute(
            any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenReturn(result);
  }

  private void givenExecuteErrors(Throwable error) {
    when(redis.execute(
            any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenReturn(Flux.error(error));
  }

  // ----- constructor guard --------------------------------------------------

  @Test
  void constructorRejectsNullRedis() {
    assertThatThrownBy(() -> new ReactiveRevisionedAuthFlowSessionRedisOperations(null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- createIfAbsent(): guards -------------------------------------------

  @Test
  void createIfAbsentBlankKeyErrors() {
    assertThatThrownBy(
            () -> newOperations().createIfAbsent("  ", "json", Duration.ofSeconds(60)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createIfAbsentBlankJsonErrors() {
    assertThatThrownBy(() -> newOperations().createIfAbsent("key", "  ", Duration.ofSeconds(60)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createIfAbsentNullTtlErrors() {
    assertThatThrownBy(() -> newOperations().createIfAbsent("key", "json", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createIfAbsentZeroTtlErrors() {
    assertThatThrownBy(() -> newOperations().createIfAbsent("key", "json", Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createIfAbsentNegativeTtlErrors() {
    assertThatThrownBy(
            () -> newOperations().createIfAbsent("key", "json", Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- createIfAbsent(): success / contention / error ---------------------

  @Test
  void createIfAbsentSucceedsWhenScriptReturnsOne() {
    givenExecuteReturns(Flux.just(1L));
    StepVerifier.create(newOperations().createIfAbsent("key", "json", Duration.ofSeconds(60)))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  void createIfAbsentFailsWhenScriptReturnsZero() {
    givenExecuteReturns(Flux.just(0L));
    StepVerifier.create(newOperations().createIfAbsent("key", "json", Duration.ofSeconds(60)))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void createIfAbsentFailsWhenScriptReturnsEmpty() {
    givenExecuteReturns(Flux.empty());
    StepVerifier.create(newOperations().createIfAbsent("key", "json", Duration.ofSeconds(60)))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void createIfAbsentRedisErrorYieldsCacheException() {
    givenExecuteErrors(new IllegalStateException("boom"));
    StepVerifier.create(newOperations().createIfAbsent("key", "json", Duration.ofSeconds(60)))
        .expectError(CacheException.class)
        .verify();
  }

  // ----- initializeLegacy(): guards -------------------------------------------

  @Test
  void initializeLegacyBlankSessionKeyErrors() {
    assertThatThrownBy(
            () -> newOperations().initializeLegacy("  ", "lock", "token", "legacy", "revised"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void initializeLegacyBlankLockKeyErrors() {
    assertThatThrownBy(
            () -> newOperations().initializeLegacy("key", "  ", "token", "legacy", "revised"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void initializeLegacyBlankTokenErrors() {
    assertThatThrownBy(
            () -> newOperations().initializeLegacy("key", "lock", "  ", "legacy", "revised"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void initializeLegacyBlankExpectedLegacyJsonErrors() {
    assertThatThrownBy(
            () -> newOperations().initializeLegacy("key", "lock", "token", "  ", "revised"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void initializeLegacyBlankRevisedJsonErrors() {
    assertThatThrownBy(
            () -> newOperations().initializeLegacy("key", "lock", "token", "legacy", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- initializeLegacy(): success / empty / error ---------------------------

  @Test
  void initializeLegacySucceeds() {
    givenExecuteReturns(Flux.just(1L));
    StepVerifier.create(
            newOperations().initializeLegacy("key", "lock", "token", "legacy", "revised"))
        .expectNext(1L)
        .verifyComplete();
  }

  @Test
  void initializeLegacyEmptyYieldsDefaultNegativeFour() {
    givenExecuteReturns(Flux.empty());
    StepVerifier.create(
            newOperations().initializeLegacy("key", "lock", "token", "legacy", "revised"))
        .expectNext(-4L)
        .verifyComplete();
  }

  @Test
  void initializeLegacyRedisErrorYieldsCacheException() {
    givenExecuteErrors(new IllegalStateException("boom"));
    StepVerifier.create(
            newOperations().initializeLegacy("key", "lock", "token", "legacy", "revised"))
        .expectError(CacheException.class)
        .verify();
  }

  // ----- replaceIfCurrent(): guards -------------------------------------------

  @Test
  void replaceIfCurrentBlankSessionKeyErrors() {
    assertThatThrownBy(() -> newOperations().replaceIfCurrent("  ", "revision", "revised"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void replaceIfCurrentBlankExpectedRevisionErrors() {
    assertThatThrownBy(() -> newOperations().replaceIfCurrent("key", "  ", "revised"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void replaceIfCurrentBlankRevisedJsonErrors() {
    assertThatThrownBy(() -> newOperations().replaceIfCurrent("key", "revision", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- replaceIfCurrent(): success / empty / error ---------------------------

  @Test
  void replaceIfCurrentSucceeds() {
    givenExecuteReturns(Flux.just(1L));
    StepVerifier.create(newOperations().replaceIfCurrent("key", "revision", "revised"))
        .expectNext(1L)
        .verifyComplete();
  }

  @Test
  void replaceIfCurrentEmptyYieldsDefaultNegativeFour() {
    givenExecuteReturns(Flux.empty());
    StepVerifier.create(newOperations().replaceIfCurrent("key", "revision", "revised"))
        .expectNext(-4L)
        .verifyComplete();
  }

  @Test
  void replaceIfCurrentRedisErrorYieldsCacheException() {
    givenExecuteErrors(new IllegalStateException("boom"));
    StepVerifier.create(newOperations().replaceIfCurrent("key", "revision", "revised"))
        .expectError(CacheException.class)
        .verify();
  }

  // ----- deleteIfCurrent(): guards -------------------------------------------

  @Test
  void deleteIfCurrentBlankSessionKeyErrors() {
    assertThatThrownBy(() -> newOperations().deleteIfCurrent("  ", "revision"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteIfCurrentBlankExpectedRevisionErrors() {
    assertThatThrownBy(() -> newOperations().deleteIfCurrent("key", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- deleteIfCurrent(): success / empty / error ---------------------------

  @Test
  void deleteIfCurrentSucceeds() {
    givenExecuteReturns(Flux.just(1L));
    StepVerifier.create(newOperations().deleteIfCurrent("key", "revision"))
        .expectNext(1L)
        .verifyComplete();
  }

  @Test
  void deleteIfCurrentEmptyYieldsDefaultNegativeFour() {
    givenExecuteReturns(Flux.empty());
    StepVerifier.create(newOperations().deleteIfCurrent("key", "revision"))
        .expectNext(-4L)
        .verifyComplete();
  }

  @Test
  void deleteIfCurrentRedisErrorYieldsCacheException() {
    givenExecuteErrors(new IllegalStateException("boom"));
    StepVerifier.create(newOperations().deleteIfCurrent("key", "revision"))
        .expectError(CacheException.class)
        .verify();
  }
}
