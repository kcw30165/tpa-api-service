package com.bct.ngtpa.apiservice.application.usecase.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.MissingRequiredGeneralMetadataException;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GeneralMetadataResolverServiceTest {
  @Test
  void completeCacheHitReturnsCachedSetWithoutSourceCall() {
    var cached = set(List.of(entry(GeneralMetadataName.FUND_PRICE, "6")));
    var sourceCalls = new AtomicInteger();
    var service =
        service(
            cache(Optional.of(cached)),
            command -> {
              sourceCalls.incrementAndGet();
              return Mono.error(new AssertionError("source must not be called"));
            });

    assertThat(service.resolve(query(GeneralMetadataName.FUND_PRICE)).block()).isSameAs(cached);
    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void cacheMissFetchesOnceUnfilteredWritesCompleteResultAndReturnsIt() {
    var cache = new RecordingCache(Optional.empty());
    var commands = new ArrayList<FetchGeneralMetadataCommand>();
    var service =
        service(
            cache,
            command -> {
              commands.add(command);
              return Mono.just(
                  new FetchGeneralMetadataResult(
                      Optional.of(GeneralMetadataResolutionSource.POLICY_SPECIFIC),
                      List.of(entry(GeneralMetadataName.FUND_PRICE, "6"))));
            });

    var resolved = service.resolve(query(GeneralMetadataName.FUND_PRICE)).block();

    assertThat(commands)
        .singleElement()
        .satisfies(
            command -> {
              assertThat(command.scope()).isEqualTo(scope());
              assertThat(command.requestedKeys()).isEmpty();
              assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
            });
    assertThat(cache.written).isSameAs(resolved);
    assertThat(resolved.resolutionSource())
        .isEqualTo(GeneralMetadataResolutionSource.POLICY_SPECIFIC);
  }

  @Test
  void incompleteCacheEntryReloadsUnfiltered() {
    var cache =
        new RecordingCache(Optional.of(set(List.of(entry(GeneralMetadataName.FX_RATE, "4")))));
    var calls = new AtomicInteger();
    var service =
        service(
            cache,
            command -> {
              calls.incrementAndGet();
              return Mono.just(
                  new FetchGeneralMetadataResult(
                      Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
                      List.of(entry(GeneralMetadataName.FUND_PRICE, "6"))));
            });

    assertThat(
            service
                .resolve(query(GeneralMetadataName.FUND_PRICE))
                .block()
                .find(key(GeneralMetadataName.FUND_PRICE)))
        .isPresent();
    assertThat(calls).hasValue(1);
  }

  @Test
  void missingAfterOneTargetedAttemptFailsWithoutCacheWrite() {
    var cache = new RecordingCache(Optional.empty());
    var service =
        service(
            cache,
            command -> Mono.just(new FetchGeneralMetadataResult(Optional.empty(), List.of())));
    assertThatThrownBy(() -> service.resolve(query(GeneralMetadataName.FUND_PRICE)).block())
        .hasRootCauseInstanceOf(MissingRequiredGeneralMetadataException.class);
    assertThat(cache.written).isNull();
  }

  @Test
  void targetedPolicySpecificResultMergesExistingKeysAndRewritesCache() {
    var cache = new RecordingCache(Optional.empty());
    var query =
        new ResolveGeneralMetadataQuery(
            scope(),
            Set.of(key(GeneralMetadataName.FUND_PRICE), key(GeneralMetadataName.FX_RATE)),
            ActorUserRole.ROLE_USER_MBR);
    var service =
        service(
            cache,
            command ->
                command.requestedKeys().isEmpty()
                    ? Mono.just(
                        new FetchGeneralMetadataResult(
                            Optional.of(GeneralMetadataResolutionSource.POLICY_SPECIFIC),
                            List.of(entry(GeneralMetadataName.FUND_PRICE, "6"))))
                    : Mono.just(
                        new FetchGeneralMetadataResult(
                            Optional.of(GeneralMetadataResolutionSource.POLICY_SPECIFIC),
                            List.of(entry(GeneralMetadataName.FX_RATE, "4")))));
    var resolved = service.resolve(query).block();
    assertThat(resolved.entries()).hasSize(2);
    assertThat(cache.written).isSameAs(resolved);
  }

  @Test
  void conflictingTargetedSourceFailsAndIsNotCached() {
    var cache = new RecordingCache(Optional.empty());
    var service =
        service(
            cache,
            command ->
                command.requestedKeys().isEmpty()
                    ? Mono.just(new FetchGeneralMetadataResult(Optional.empty(), List.of()))
                    : Mono.just(
                        new FetchGeneralMetadataResult(
                            Optional.of(GeneralMetadataResolutionSource.POLICY_SPECIFIC),
                            List.of(entry(GeneralMetadataName.FUND_PRICE, "6")))));
    assertThatThrownBy(() -> service.resolve(query(GeneralMetadataName.FUND_PRICE)).block())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting general metadata resolution sources");
    assertThat(cache.written).isNull();
  }

  @Test
  void sourceAndCacheFailuresRemainFailures() {
    var sourceFailure = new IllegalStateException("source failed");
    assertThatThrownBy(
            () ->
                service(cache(Optional.empty()), command -> Mono.error(sourceFailure))
                    .resolve(query(GeneralMetadataName.FUND_PRICE))
                    .block())
        .hasRootCause(sourceFailure);

    var cacheFailure = new IllegalStateException("cache failed");
    var failingCache =
        new GeneralMetadataCachePort() {
          public Mono<Optional<ResolvedGeneralMetadataSet>> read(GeneralMetadataScope scope) {
            return Mono.error(cacheFailure);
          }

          public Mono<Void> write(ResolvedGeneralMetadataSet metadata) {
            return Mono.error(cacheFailure);
          }

          public Mono<Void> evict(GeneralMetadataScope scope) {
            return Mono.error(cacheFailure);
          }

          @Override
          public Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
              GeneralMetadataScope scope,
              ResolvedGeneralMetadataSet expected,
              ResolvedGeneralMetadataSet replacement) {
            return Mono.just(GeneralMetadataCacheReplaceResult.APPLIED);
          }
        };
    assertThatThrownBy(
            () ->
                service(failingCache, command -> Mono.error(new AssertionError()))
                    .resolve(query(GeneralMetadataName.FUND_PRICE))
                    .block())
        .hasRootCause(cacheFailure);
  }

  @Test
  void missingAfterUnfilteredTriggersOneTargetedBatchCommand() {
    var commands = new ArrayList<FetchGeneralMetadataCommand>();
    var service =
        service(
            cache(Optional.empty()),
            command -> {
              commands.add(command);
              if (command.requestedKeys().isEmpty()) {
                return Mono.just(new FetchGeneralMetadataResult(Optional.empty(), List.of()));
              }
              return Mono.just(
                  new FetchGeneralMetadataResult(
                      Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
                      List.of(entry(GeneralMetadataName.FUND_PRICE, "6"))));
            });
    assertThat(
            service
                .resolve(query(GeneralMetadataName.FUND_PRICE))
                .block()
                .find(key(GeneralMetadataName.FUND_PRICE)))
        .isPresent();
    assertThat(commands).hasSize(2);
    assertThat(commands.get(0).requestedKeys()).isEmpty();
    assertThat(commands.get(1).requestedKeys())
        .containsExactly(key(GeneralMetadataName.FUND_PRICE));
  }

  @Test
  void multipleMissingKeysUseOneResolverBatchCommand() {
    var commands = new ArrayList<FetchGeneralMetadataCommand>();
    var query =
        new ResolveGeneralMetadataQuery(
            scope(),
            Set.of(key(GeneralMetadataName.FUND_PRICE), key(GeneralMetadataName.FX_RATE)),
            ActorUserRole.ROLE_USER_MBR);
    var service =
        service(
            cache(Optional.empty()),
            command -> {
              commands.add(command);
              return command.requestedKeys().isEmpty()
                  ? Mono.just(new FetchGeneralMetadataResult(Optional.empty(), List.of()))
                  : Mono.just(
                      new FetchGeneralMetadataResult(
                          Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
                          List.of(
                              entry(GeneralMetadataName.FUND_PRICE, "6"),
                              entry(GeneralMetadataName.FX_RATE, "4"))));
            });
    assertThat(service.resolve(query).block().entries()).hasSize(2);
    assertThat(commands).hasSize(2);
    assertThat(commands.get(1).requestedKeys())
        .containsExactlyInAnyOrder(
            key(GeneralMetadataName.FUND_PRICE), key(GeneralMetadataName.FX_RATE));
  }

  @Test
  void targetedDefaultAllResultIsPersisted() {
    var cache = new RecordingCache(Optional.empty());
    var service =
        service(
            cache,
            command ->
                command.requestedKeys().isEmpty()
                    ? Mono.just(new FetchGeneralMetadataResult(Optional.empty(), List.of()))
                    : Mono.just(
                        new FetchGeneralMetadataResult(
                            Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
                            List.of(entry(GeneralMetadataName.FUND_PRICE, "6")))));
    var resolved = service.resolve(query(GeneralMetadataName.FUND_PRICE)).block();
    assertThat(resolved.resolutionSource()).isEqualTo(GeneralMetadataResolutionSource.DEFAULT_ALL);
    assertThat(cache.written).isSameAs(resolved);
  }

  @Test
  void conflictingTargetedValueFailsWithoutCacheWrite() {
    var cache = new RecordingCache(Optional.empty());
    var query =
        new ResolveGeneralMetadataQuery(
            scope(),
            Set.of(key(GeneralMetadataName.FUND_PRICE), key(GeneralMetadataName.FX_RATE)),
            ActorUserRole.ROLE_USER_MBR);
    var service =
        service(
            cache,
            command ->
                command.requestedKeys().isEmpty()
                    ? Mono.just(
                        new FetchGeneralMetadataResult(
                            Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
                            List.of(entry(GeneralMetadataName.FUND_PRICE, "6"))))
                    : Mono.just(
                        new FetchGeneralMetadataResult(
                            Optional.of(GeneralMetadataResolutionSource.DEFAULT_ALL),
                            List.of(
                                entry(GeneralMetadataName.FUND_PRICE, "7"),
                                entry(GeneralMetadataName.FX_RATE, "4")))));
    assertThatThrownBy(() -> service.resolve(query).block())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting resolved general metadata entry");
    assertThat(cache.written).isNull();
  }

  private GeneralMetadataResolverService service(
      GeneralMetadataCachePort cache, GeneralMetadataSourcePort source) {
    return new GeneralMetadataResolverService(cache, source);
  }

  private ResolveGeneralMetadataQuery query(GeneralMetadataName name) {
    return new ResolveGeneralMetadataQuery(scope(), Set.of(key(name)), ActorUserRole.ROLE_USER_MBR);
  }

  private GeneralMetadataScope scope() {
    return new GeneralMetadataScope("DB", "DBA", "00000008802");
  }

  private GeneralMetadataKey key(GeneralMetadataName name) {
    return new GeneralMetadataKey(name, GeneralMetadataType.PRECISION);
  }

  private ResolvedGeneralMetadata entry(GeneralMetadataName name, String value) {
    return new ResolvedGeneralMetadata(key(name), value);
  }

  private ResolvedGeneralMetadataSet set(List<ResolvedGeneralMetadata> entries) {
    return new ResolvedGeneralMetadataSet(
        scope(), GeneralMetadataResolutionSource.DEFAULT_ALL, entries);
  }

  private GeneralMetadataCachePort cache(Optional<ResolvedGeneralMetadataSet> value) {
    return new RecordingCache(value);
  }

  private static final class RecordingCache implements GeneralMetadataCachePort {
    private final Optional<ResolvedGeneralMetadataSet> value;
    private ResolvedGeneralMetadataSet written;

    private RecordingCache(Optional<ResolvedGeneralMetadataSet> value) {
      this.value = value;
    }

    public Mono<Optional<ResolvedGeneralMetadataSet>> read(GeneralMetadataScope scope) {
      return Mono.just(value);
    }

    public Mono<Void> write(ResolvedGeneralMetadataSet metadata) {
      written = metadata;
      return Mono.empty();
    }

    public Mono<Void> evict(GeneralMetadataScope scope) {
      return Mono.empty();
    }

    @Override
    public Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
        GeneralMetadataScope scope,
        ResolvedGeneralMetadataSet expected,
        ResolvedGeneralMetadataSet replacement) {
      return Mono.just(GeneralMetadataCacheReplaceResult.APPLIED);
    }
  }
}
