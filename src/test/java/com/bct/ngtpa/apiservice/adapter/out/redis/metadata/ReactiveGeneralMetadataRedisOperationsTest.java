package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SuppressWarnings({"unchecked", "rawtypes"})
class ReactiveGeneralMetadataRedisOperationsTest {
  private static final String KEY = "ngtpa:general-metadata:" + "a".repeat(64);
  private static final String EXPECTED = "{\"source\":\"DEFAULT_ALL\",\"entries\":[{\"value\":\"6\"}]}";
  private static final String REPLACEMENT = "{\"source\":\"DEFAULT_ALL\",\"entries\":[{\"value\":\"7\"}]}";

  private final ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
  private final ReactiveGeneralMetadataRedisOperations operations =
      new ReactiveGeneralMetadataRedisOperations(redis);

  @Test
  void mapsOnlyDocumentedRedisResults() {
    assertResult(1L, GeneralMetadataCacheReplaceResult.APPLIED);
    assertResult(0L, GeneralMetadataCacheReplaceResult.CONFLICT);
    assertResult(-1L, GeneralMetadataCacheReplaceResult.MISSING);
  }

  @Test
  void rejectsUnsupportedPositiveResultWithoutLeakingInputs() {
    assertUnsupported(2L);
  }

  @Test
  void rejectsUnsupportedNegativeResultWithoutLeakingInputs() {
    assertUnsupported(-2L);
  }

  @Test
  void emptyScriptPublisherFailsClosedWithoutLeakingInputs() {
    when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.empty());

    StepVerifier.create(operations.replaceIfUnchanged(KEY, EXPECTED, REPLACEMENT))
        .expectErrorSatisfies(this::assertSanitizedFailure)
        .verify();
  }

  @Test
  void redisFailureIsSafelyWrappedWithoutLeakingInputs() {
    when(redis.execute(any(RedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.error(new IllegalStateException("redis unavailable at internal-host")));

    StepVerifier.create(operations.replaceIfUnchanged(KEY, EXPECTED, REPLACEMENT))
        .expectErrorSatisfies(
            error -> {
              assertSanitizedFailure(error);
              assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
            })
        .verify();
  }

  @Test
  void scriptAndArgumentOrderMatchAtomicExpectedValueContract() {
    when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(1L));

    StepVerifier.create(operations.replaceIfUnchanged(KEY, EXPECTED, REPLACEMENT))
        .expectNext(GeneralMetadataCacheReplaceResult.APPLIED)
        .verifyComplete();

    ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
    ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> args = ArgumentCaptor.forClass(List.class);
    verify(redis).execute(script.capture(), keys.capture(), args.capture());

    assertThat(keys.getValue()).containsExactly(KEY);
    assertThat(args.getValue()).containsExactly(EXPECTED, REPLACEMENT);
    assertThat(script.getValue().getScriptAsString())
        .contains(
            "redis.call('GET',KEYS[1])",
            "current~=ARGV[1]",
            "redis.call('PTTL',KEYS[1])",
            "ttl<=0",
            "'KEEPTTL'");
  }

  private void assertResult(long redisResult, GeneralMetadataCacheReplaceResult expected) {
    when(redis.execute(any(RedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.just(redisResult));
    StepVerifier.create(operations.replaceIfUnchanged(KEY, EXPECTED, REPLACEMENT))
        .expectNext(expected)
        .verifyComplete();
  }

  private void assertUnsupported(long redisResult) {
    when(redis.execute(any(RedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.just(redisResult));
    StepVerifier.create(operations.replaceIfUnchanged(KEY, EXPECTED, REPLACEMENT))
        .expectErrorSatisfies(
            error -> {
              assertSanitizedFailure(error);
              assertThat(error.getMessage()).doesNotContain(Long.toString(redisResult));
            })
        .verify();
  }

  private void assertSanitizedFailure(Throwable error) {
    assertThat(error.getMessage())
        .isEqualTo("Cache replace operation failed")
        .doesNotContain(KEY, EXPECTED, REPLACEMENT, "internal-host");
  }
}
