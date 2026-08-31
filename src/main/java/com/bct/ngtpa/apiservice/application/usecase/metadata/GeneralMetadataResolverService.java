package com.bct.ngtpa.apiservice.application.usecase.metadata;

import com.bct.ngtpa.apiservice.application.dto.metadata.FetchGeneralMetadataCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.FetchGeneralMetadataResult;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolveGeneralMetadataQuery;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.exception.MissingRequiredGeneralMetadataException;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

public final class GeneralMetadataResolverService implements GeneralMetadataResolver {
  private final GeneralMetadataCachePort cache;
  private final GeneralMetadataSourcePort source;
  private final ConcurrentMap<InFlightKey, Mono<ResolvedGeneralMetadataSet>> inFlight =
      new ConcurrentHashMap<>();

  public GeneralMetadataResolverService(
      GeneralMetadataCachePort cache, GeneralMetadataSourcePort source) {
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
  }

  @Override
  public Mono<ResolvedGeneralMetadataSet> resolve(ResolveGeneralMetadataQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    return cache
        .read(query.scope())
        .flatMap(
            cached -> {
              if (cached.isPresent() && containsAll(cached.get(), query.requiredKeys())) {
                return Mono.just(cached.get());
              }
              return coalesce(query, cached);
            });
  }

  private Mono<ResolvedGeneralMetadataSet> coalesce(
      ResolveGeneralMetadataQuery query, Optional<ResolvedGeneralMetadataSet> cached) {
    InFlightKey key = new InFlightKey(query.scope(), query.requiredKeys());
    AtomicReference<Mono<ResolvedGeneralMetadataSet>> created = new AtomicReference<>();
    Mono<ResolvedGeneralMetadataSet> shared =
        inFlight.computeIfAbsent(
            key,
            ignored -> {
              Mono<ResolvedGeneralMetadataSet> candidate =
                  Mono.defer(() -> loadUnfiltered(query, cached))
                      .doFinally(signal -> inFlight.remove(key, created.get()))
                      .cache();
              created.set(candidate);
              return candidate;
            });
    return shared.doOnCancel(() -> inFlight.remove(key, shared));
  }

  int inFlightCountForTesting() {
    return inFlight.size();
  }

  private Mono<ResolvedGeneralMetadataSet> loadUnfiltered(
      ResolveGeneralMetadataQuery query, Optional<ResolvedGeneralMetadataSet> cached) {
    return source
        .fetch(new FetchGeneralMetadataCommand(query.scope(), Set.of(), query.actorUserRole()))
        .flatMap(result -> continueWithTargetedIfNeeded(query, cached, result));
  }

  private Mono<ResolvedGeneralMetadataSet> continueWithTargetedIfNeeded(
      ResolveGeneralMetadataQuery query,
      Optional<ResolvedGeneralMetadataSet> cached,
      FetchGeneralMetadataResult unfiltered) {
    var missing = missingKeys(unfiltered, query.requiredKeys());
    if (missing.isEmpty()) {
      return persistComplete(query, unfiltered);
    }
    return source
        .fetch(new FetchGeneralMetadataCommand(query.scope(), missing, query.actorUserRole()))
        .flatMap(targeted -> persistTargeted(query, cached, merge(unfiltered, targeted)));
  }

  private Mono<ResolvedGeneralMetadataSet> persistTargeted(
      ResolveGeneralMetadataQuery query,
      Optional<ResolvedGeneralMetadataSet> cached,
      FetchGeneralMetadataResult merged) {
    ResolvedGeneralMetadataSet replacement = normalizeComplete(query, merged, true);
    if (cached.isEmpty()) {
      return cache.write(replacement).thenReturn(replacement);
    }
    return cache
        .replaceIfUnchanged(query.scope(), cached.orElseThrow(), replacement)
        .flatMap(
            outcome ->
                outcome == GeneralMetadataCacheReplaceResult.APPLIED
                    ? Mono.just(replacement)
                    : recoverAfterConflict(query));
  }

  private Mono<ResolvedGeneralMetadataSet> recoverAfterConflict(ResolveGeneralMetadataQuery query) {
    return cache
        .read(query.scope())
        .flatMap(
            reread ->
                reread
                    .filter(value -> containsAll(value, query.requiredKeys()))
                    .map(Mono::just)
                    .orElseGet(() -> Mono.error(new GeneralMetadataConcurrencyException())));
  }

  private ResolvedGeneralMetadataSet normalizeComplete(
      ResolveGeneralMetadataQuery query,
      FetchGeneralMetadataResult result,
      boolean requireAllKeys) {
    GeneralMetadataResolutionSource resolutionSource =
        result.resolutionSource().orElseThrow(MissingRequiredGeneralMetadataException::new);
    ResolvedGeneralMetadataSet normalized =
        ResolvedGeneralMetadataSet.normalize(query.scope(), resolutionSource, result.entries());
    if (requireAllKeys && !containsAll(normalized, query.requiredKeys())) {
      throw new MissingRequiredGeneralMetadataException();
    }
    return normalized;
  }

  private Mono<ResolvedGeneralMetadataSet> persistComplete(
      ResolveGeneralMetadataQuery query, FetchGeneralMetadataResult result) {
    if (result.resolutionSource().isEmpty()) {
      return Mono.error(new MissingRequiredGeneralMetadataException());
    }
    var metadata =
        ResolvedGeneralMetadataSet.normalize(
            query.scope(), result.resolutionSource().orElseThrow(), result.entries());
    if (!containsAll(metadata, query.requiredKeys())) {
      return Mono.error(new MissingRequiredGeneralMetadataException());
    }
    return cache.write(metadata).thenReturn(metadata);
  }

  private FetchGeneralMetadataResult merge(
      FetchGeneralMetadataResult existing, FetchGeneralMetadataResult targeted) {
    if (targeted.resolutionSource().isEmpty()) {
      return existing;
    }
    if (existing.resolutionSource().isPresent()
        && existing.resolutionSource().orElseThrow() != targeted.resolutionSource().orElseThrow()) {
      throw new IllegalArgumentException("Conflicting general metadata resolution sources");
    }
    GeneralMetadataResolutionSource sourceValue =
        existing.resolutionSource().orElseGet(targeted.resolutionSource()::orElseThrow);
    var entries = new LinkedHashMap<GeneralMetadataKey, ResolvedGeneralMetadata>();
    for (var entry : existing.entries()) entries.put(entry.key(), entry);
    for (var entry : targeted.entries()) {
      var previous = entries.putIfAbsent(entry.key(), entry);
      if (previous != null && !previous.value().equals(entry.value())) {
        throw new IllegalArgumentException("Conflicting resolved general metadata entry");
      }
    }
    return new FetchGeneralMetadataResult(
        java.util.Optional.of(sourceValue), new ArrayList<>(entries.values()));
  }

  private Set<GeneralMetadataKey> missingKeys(
      FetchGeneralMetadataResult result, Set<GeneralMetadataKey> required) {
    var present =
        result.entries().stream()
            .map(ResolvedGeneralMetadata::key)
            .collect(java.util.stream.Collectors.toSet());
    return required.stream()
        .filter(key -> !present.contains(key))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private boolean containsAll(ResolvedGeneralMetadataSet metadata, Set<GeneralMetadataKey> keys) {
    return keys.stream().allMatch(key -> metadata.find(key).isPresent());
  }

  private record InFlightKey(GeneralMetadataScope scope, Set<GeneralMetadataKey> requiredKeys) {
    private InFlightKey {
      Objects.requireNonNull(scope, "scope must not be null");
      requiredKeys =
          Set.copyOf(Objects.requireNonNull(requiredKeys, "requiredKeys must not be null"));
    }
  }
}
