package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RedisGeneralMetadataCacheAdapterTest {
  @Test
  void missReturnsEmptyAndUsesOpaquePolicyScopedKey() {
    var cache = new RecordingCache();
    var adapter = adapter(cache);
    assertThat(adapter.read(scope()).block()).isEmpty();
    assertThat(cache.lastKey).startsWith("ngtpa:general-metadata:DB:");
    assertThat(cache.lastKey).doesNotContain("DBA", "00000008802");
  }

  @Test
  void writesAndReadsDefaultAndPolicySpecificDocumentsWithPt20m() {
    for (var source : GeneralMetadataResolutionSource.values()) {
      var cache = new RecordingCache();
      var adapter = adapter(cache);
      var metadata = new ResolvedGeneralMetadataSet(scope(), source, List.of(entry("6")));
      adapter.write(metadata).block();
      assertThat(cache.ttl).isEqualTo(Duration.parse("PT20M"));
      assertThat(adapter.read(scope()).block()).contains(metadata);
    }
  }

  @Test
  void corruptEntryIsEvictedAndTreatedAsMiss() {
    var cache = new RecordingCache();
    cache.value = "not-json";
    var adapter = adapter(cache);
    assertThat(adapter.read(scope()).block()).isEmpty();
    assertThat(cache.evicted).isTrue();
  }

  @Test
  void redisFailuresRemainFailures() {
    var failure = new IllegalStateException("redis failed");
    var adapter =
        new RedisGeneralMetadataCacheAdapter(
            new FailingCache(failure),
            new RedisCacheKeyFactory("ngtpa"),
            new ObjectMapper().findAndRegisterModules(),
            new GeneralMetadataCacheProperties(Duration.parse("PT20M")),
            (key, expected, replacement) ->
                Mono.just(
                    com.bct.ngtpa.apiservice.application.dto.metadata
                        .GeneralMetadataCacheReplaceResult.APPLIED));
    assertThatThrownBy(() -> adapter.read(scope()).block()).isSameAs(failure);
    assertThatThrownBy(
            () ->
                adapter
                    .write(
                        new ResolvedGeneralMetadataSet(
                            scope(),
                            GeneralMetadataResolutionSource.DEFAULT_ALL,
                            List.of(entry("6"))))
                    .block())
        .isSameAs(failure);
  }

  private RedisGeneralMetadataCacheAdapter adapter(CachePort cache) {
    return new RedisGeneralMetadataCacheAdapter(
        cache,
        new RedisCacheKeyFactory("ngtpa"),
        new ObjectMapper().findAndRegisterModules(),
        new GeneralMetadataCacheProperties(Duration.parse("PT20M")),
        (key, expected, replacement) ->
            Mono.just(
                com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult
                    .APPLIED));
  }

  private GeneralMetadataScope scope() {
    return new GeneralMetadataScope("DB", "DBA", "00000008802");
  }

  private ResolvedGeneralMetadata entry(String value) {
    return new ResolvedGeneralMetadata(
        new GeneralMetadataKey(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION),
        value);
  }

  private static final class RecordingCache implements CachePort {
    String lastKey;
    String value;
    Duration ttl;
    boolean evicted;

    public Mono<Optional<String>> get(String key) {
      lastKey = key;
      return Mono.just(Optional.ofNullable(value));
    }

    public Mono<Void> set(String key, String value, Duration ttl) {
      lastKey = key;
      this.value = value;
      this.ttl = ttl;
      return Mono.empty();
    }

    public Mono<Boolean> evict(String key) {
      lastKey = key;
      evicted = true;
      value = null;
      return Mono.just(true);
    }
  }

  private record FailingCache(RuntimeException failure) implements CachePort {
    public Mono<Optional<String>> get(String key) {
      return Mono.error(failure);
    }

    public Mono<Void> set(String key, String value, Duration ttl) {
      return Mono.error(failure);
    }

    public Mono<Boolean> evict(String key) {
      return Mono.error(failure);
    }
  }

  // ----- replaceIfUnchanged() (added round 138 - not covered by the
  // original file's tests, which predate this method) ------------------------

  @Test
  void replaceIfUnchangedMismatchedExpectedScopeErrors() {
    var cache = new RecordingCache();
    var adapter = adapter(cache);
    var wrongScope = new GeneralMetadataScope("OTHER", "DBA", "00000008802");
    var expected =
        new ResolvedGeneralMetadataSet(wrongScope, GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("1")));
    var replacement =
        new ResolvedGeneralMetadataSet(scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("2")));
    assertThatThrownBy(() -> adapter.replaceIfUnchanged(scope(), expected, replacement).block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void replaceIfUnchangedMismatchedReplacementScopeErrors() {
    var cache = new RecordingCache();
    var adapter = adapter(cache);
    var wrongScope = new GeneralMetadataScope("OTHER", "DBA", "00000008802");
    var expected =
        new ResolvedGeneralMetadataSet(scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("1")));
    var replacement =
        new ResolvedGeneralMetadataSet(wrongScope, GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("2")));
    assertThatThrownBy(() -> adapter.replaceIfUnchanged(scope(), expected, replacement).block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void replaceIfUnchangedSucceedsWhenApplied() {
    var cache = new RecordingCache();
    var adapter = adapter(cache);
    var expected =
        new ResolvedGeneralMetadataSet(scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("1")));
    var replacement =
        new ResolvedGeneralMetadataSet(scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("2")));
    var result = adapter.replaceIfUnchanged(scope(), expected, replacement).block();
    assertThat(result)
        .isEqualTo(
            com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult.APPLIED);
  }

  @Test
  void replaceIfUnchangedDelegatesConflictFromOperations() {
    var cache = new RecordingCache();
    var adapter =
        new RedisGeneralMetadataCacheAdapter(
            cache,
            new RedisCacheKeyFactory("ngtpa"),
            new ObjectMapper().findAndRegisterModules(),
            new GeneralMetadataCacheProperties(Duration.parse("PT20M")),
            (key, expectedJson, replacementJson) ->
                Mono.just(
                    com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult
                        .CONFLICT));
    var expected =
        new ResolvedGeneralMetadataSet(scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("1")));
    var replacement =
        new ResolvedGeneralMetadataSet(scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry("2")));
    var result = adapter.replaceIfUnchanged(scope(), expected, replacement).block();
    assertThat(result)
        .isEqualTo(
            com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult.CONFLICT);
  }
}
