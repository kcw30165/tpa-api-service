package com.bct.ngtpa.apiservice.application.usecase.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class GeneralMetadataResolverConcurrencyBehaviorTest {
  private static final GeneralMetadataScope SCOPE =
      new GeneralMetadataScope("DB", "DBA", "00000008802");
  private static final GeneralMetadataScope OTHER_SCOPE =
      new GeneralMetadataScope("OI", "JPM", "00000008803");
  private static final GeneralMetadataKey NAV =
      new GeneralMetadataKey(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION);
  private static final GeneralMetadataKey UNIT =
      new GeneralMetadataKey(GeneralMetadataName.FX_RATE, GeneralMetadataType.PRECISION);

  @Test
  void concurrentSameScopeSuccessSharesOneSourceFlowAndCleansUp() {
    var cache = new ScriptedCache();
    var source = new GatedSource();
    var service = new GeneralMetadataResolverService(cache, source);
    Mono<ResolvedGeneralMetadataSet> first = service.resolve(query(SCOPE, NAV));
    Mono<ResolvedGeneralMetadataSet> second = service.resolve(query(SCOPE, NAV));

    StepVerifier.create(Mono.zip(first, second))
        .then(() -> source.complete(result(NAV, "6")))
        .assertNext(pair -> assertThat(pair.getT1()).isEqualTo(pair.getT2()))
        .verifyComplete();

    assertThat(source.calls).hasValue(1);
    assertThat(service.inFlightCountForTesting()).isZero();
  }

  @Test
  void concurrentSameScopeFailureIsSharedAndSubsequentRequestRetries() {
    var cache = new ScriptedCache();
    var source = new GatedSource();
    var service = new GeneralMetadataResolverService(cache, source);
    Mono<ResolvedGeneralMetadataSet> shared =
        Mono.zip(service.resolve(query(SCOPE, NAV)), service.resolve(query(SCOPE, NAV)))
            .map(tuple -> tuple.getT1());

    StepVerifier.create(shared)
        .then(() -> source.fail(new IllegalStateException("source failed")))
        .expectErrorMessage("source failed")
        .verify();

    assertThat(source.calls).hasValue(1);
    assertThat(service.inFlightCountForTesting()).isZero();
    source.reset();
    StepVerifier.create(service.resolve(query(SCOPE, NAV)))
        .then(() -> source.complete(result(NAV, "6")))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(source.calls).hasValue(2);
  }

  @Test
  void cancellationCleansUpAndAllowsRetry() {
    var cache = new ScriptedCache();
    var source = new GatedSource();
    var service = new GeneralMetadataResolverService(cache, source);
    Disposable subscription = service.resolve(query(SCOPE, NAV)).subscribe();
    assertThat(service.inFlightCountForTesting()).isOne();
    subscription.dispose();
    assertThat(service.inFlightCountForTesting()).isZero();
  }

  @Test
  void differentScopesRemainIndependent() {
    var cache = new ScriptedCache();
    GeneralMetadataSourcePort source = command -> Mono.just(result(NAV, "6"));
    var service = new GeneralMetadataResolverService(cache, source);
    StepVerifier.create(
            Mono.zip(service.resolve(query(SCOPE, NAV)), service.resolve(query(OTHER_SCOPE, NAV))))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(cache.writes).hasSize(2);
  }

  @Test
  void sameScopeDifferentRequiredKeysDoNotShareAnIncompleteResult() {
    var cache = new ScriptedCache();
    GeneralMetadataSourcePort source =
        command -> {
          if (command.requestedKeys().isEmpty()) {
            return Mono.just(result(NAV, "6"));
          }
          return Mono.just(result(UNIT, "2"));
        };
    var service = new GeneralMetadataResolverService(cache, source);

    StepVerifier.create(
            Mono.zip(service.resolve(query(SCOPE, NAV)), service.resolve(query(SCOPE, NAV, UNIT))))
        .assertNext(
            pair -> {
              assertThat(pair.getT1().find(NAV)).isPresent();
              assertThat(pair.getT2().find(NAV)).isPresent();
              assertThat(pair.getT2().find(UNIT)).isPresent();
            })
        .verifyComplete();
  }

  @Test
  void emptyCacheTargetedCompletionUsesOneCompleteWriteAndNoCas() {
    var cache = new ScriptedCache();
    var source = new SequenceSource(result(NAV, "6"), result(UNIT, "2"));
    var service = new GeneralMetadataResolverService(cache, source);

    StepVerifier.create(service.resolve(query(SCOPE, NAV, UNIT)))
        .assertNext(
            value -> {
              assertThat(value.find(NAV)).isPresent();
              assertThat(value.find(UNIT)).isPresent();
            })
        .verifyComplete();

    assertThat(cache.writes)
        .singleElement()
        .satisfies(
            value -> {
              assertThat(value.find(NAV)).isPresent();
              assertThat(value.find(UNIT)).isPresent();
            });
    assertThat(cache.replaceCalls).hasValue(0);
  }

  @Test
  void existingPartialCacheUsesOneCasAndNoUnconditionalWrite() {
    var cache = ScriptedCache.incomplete(existing(NAV, "6"));
    cache.replaceResults.add(GeneralMetadataCacheReplaceResult.APPLIED);
    var source = new SequenceSource(result(NAV, "6"), result(UNIT, "2"));
    var service = new GeneralMetadataResolverService(cache, source);

    StepVerifier.create(service.resolve(query(SCOPE, NAV, UNIT)))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(cache.replaceCalls).hasValue(1);
    assertThat(cache.writes).isEmpty();
  }

  @Test
  void appliedCasReturnsReplacementWithoutRecoveryRead() {
    var cache = ScriptedCache.incomplete(existing(NAV, "6"));
    cache.replaceResults.add(GeneralMetadataCacheReplaceResult.APPLIED);
    var source = new SequenceSource(result(NAV, "6"), result(UNIT, "2"));
    var service = new GeneralMetadataResolverService(cache, source);
    StepVerifier.create(service.resolve(query(SCOPE, NAV, UNIT)))
        .assertNext(value -> assertThat(value.find(UNIT)).isPresent())
        .verifyComplete();
    assertThat(cache.replaceCalls).hasValue(1);
    assertThat(cache.readCalls).hasValue(1);
    assertThat(source.calls).hasValue(2);
  }

  @Test
  void conflictAndMissingEachPerformOneBoundedRereadAndPreserveCompetingValue() {
    for (GeneralMetadataCacheReplaceResult outcome :
        List.of(
            GeneralMetadataCacheReplaceResult.CONFLICT,
            GeneralMetadataCacheReplaceResult.MISSING)) {
      var cache = ScriptedCache.incomplete(existing(NAV, "6"));
      cache.replaceResults.add(outcome);
      cache.readResults.add(Optional.of(existing(NAV, "6", UNIT, "9")));
      var source = new SequenceSource(result(NAV, "6"), result(UNIT, "2"));
      var service = new GeneralMetadataResolverService(cache, source);
      StepVerifier.create(service.resolve(query(SCOPE, NAV, UNIT)))
          .assertNext(value -> assertThat(value.find(UNIT).orElseThrow().value()).isEqualTo("9"))
          .verifyComplete();
      assertThat(cache.replaceCalls).hasValue(1);
      assertThat(cache.readCalls).hasValue(2);
      assertThat(source.calls).hasValue(2);
    }
  }

  @Test
  void absentOrIncompleteConflictRereadFailsWithoutRetryingSourceOrCas() {
    for (Optional<ResolvedGeneralMetadataSet> reread :
        List.of(Optional.<ResolvedGeneralMetadataSet>empty(), Optional.of(existing(NAV, "6")))) {
      var cache = ScriptedCache.incomplete(existing(NAV, "6"));
      cache.replaceResults.add(GeneralMetadataCacheReplaceResult.CONFLICT);
      cache.readResults.add(reread);
      var source = new SequenceSource(result(NAV, "6"), result(UNIT, "2"));
      var service = new GeneralMetadataResolverService(cache, source);
      StepVerifier.create(service.resolve(query(SCOPE, NAV, UNIT)))
          .expectError(GeneralMetadataConcurrencyException.class)
          .verify();
      assertThat(cache.replaceCalls).hasValue(1);
      assertThat(cache.readCalls).hasValue(2);
      assertThat(source.calls).hasValue(2);
    }
  }

  private static ResolveGeneralMetadataQuery query(
      GeneralMetadataScope scope, GeneralMetadataKey... keys) {
    return new ResolveGeneralMetadataQuery(scope, Set.of(keys), ActorUserRole.ROLE_USER_MBR);
  }

  private static FetchGeneralMetadataResult result(GeneralMetadataKey key, String value) {
    return new FetchGeneralMetadataResult(
        Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
        List.of(new ResolvedGeneralMetadata(key, value)));
  }

  private static ResolvedGeneralMetadataSet existing(GeneralMetadataKey key, String value) {
    return ResolvedGeneralMetadataSet.normalize(
        SCOPE,
        GeneralMetadataResolutionSource.DEFAULT_ALL,
        List.of(new ResolvedGeneralMetadata(key, value)));
  }

  private static ResolvedGeneralMetadataSet existing(
      GeneralMetadataKey first, String firstValue, GeneralMetadataKey second, String secondValue) {
    return ResolvedGeneralMetadataSet.normalize(
        SCOPE,
        GeneralMetadataResolutionSource.DEFAULT_ALL,
        List.of(
            new ResolvedGeneralMetadata(first, firstValue),
            new ResolvedGeneralMetadata(second, secondValue)));
  }

  private static final class GatedSource implements GeneralMetadataSourcePort {
    private final AtomicInteger calls = new AtomicInteger();
    private Sinks.One<FetchGeneralMetadataResult> gate = Sinks.one();

    public Mono<FetchGeneralMetadataResult> fetch(FetchGeneralMetadataCommand command) {
      calls.incrementAndGet();
      return gate.asMono();
    }

    void complete(FetchGeneralMetadataResult result) {
      gate.tryEmitValue(result).orThrow();
    }

    void fail(Throwable failure) {
      gate.tryEmitError(failure).orThrow();
    }

    void reset() {
      gate = Sinks.one();
    }
  }

  private static final class SequenceSource implements GeneralMetadataSourcePort {
    private final AtomicInteger calls = new AtomicInteger();
    private final Queue<FetchGeneralMetadataResult> results;

    SequenceSource(FetchGeneralMetadataResult... results) {
      this.results = new ArrayDeque<>(List.of(results));
    }

    public Mono<FetchGeneralMetadataResult> fetch(FetchGeneralMetadataCommand command) {
      calls.incrementAndGet();
      return Mono.just(results.remove());
    }
  }

  private static final class ScriptedCache implements GeneralMetadataCachePort {
    private final AtomicInteger readCalls = new AtomicInteger();
    private final AtomicInteger replaceCalls = new AtomicInteger();
    private final Queue<Optional<ResolvedGeneralMetadataSet>> readResults = new ArrayDeque<>();
    private final Queue<GeneralMetadataCacheReplaceResult> replaceResults = new ArrayDeque<>();
    private final List<ResolvedGeneralMetadataSet> writes = new ArrayList<>();

    static ScriptedCache incomplete(ResolvedGeneralMetadataSet value) {
      var cache = new ScriptedCache();
      cache.readResults.add(Optional.of(value));
      return cache;
    }

    public Mono<Optional<ResolvedGeneralMetadataSet>> read(GeneralMetadataScope scope) {
      readCalls.incrementAndGet();
      return Mono.just(readResults.isEmpty() ? Optional.empty() : readResults.remove());
    }

    public Mono<Void> write(ResolvedGeneralMetadataSet metadata) {
      writes.add(metadata);
      return Mono.empty();
    }

    public Mono<Void> evict(GeneralMetadataScope scope) {
      return Mono.empty();
    }

    public Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
        GeneralMetadataScope scope,
        ResolvedGeneralMetadataSet expected,
        ResolvedGeneralMetadataSet replacement) {
      replaceCalls.incrementAndGet();
      return Mono.just(replaceResults.remove());
    }
  }
}
