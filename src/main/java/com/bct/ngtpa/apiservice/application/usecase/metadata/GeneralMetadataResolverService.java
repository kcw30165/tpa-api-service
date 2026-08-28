package com.bct.ngtpa.apiservice.application.usecase.metadata;

import com.bct.ngtpa.apiservice.application.dto.metadata.FetchGeneralMetadataCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.FetchGeneralMetadataResult;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolveGeneralMetadataQuery;
import com.bct.ngtpa.apiservice.application.exception.MissingRequiredGeneralMetadataException;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class GeneralMetadataResolverService implements GeneralMetadataResolver {
  private final GeneralMetadataCachePort cache;
  private final GeneralMetadataSourcePort source;

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
            cached ->
                cached.filter(metadata -> containsAll(metadata, query))
                    .map(Mono::just)
                    .orElseGet(() -> loadUnfiltered(query)));
  }

  private Mono<ResolvedGeneralMetadataSet> loadUnfiltered(ResolveGeneralMetadataQuery query) {
    var command = new FetchGeneralMetadataCommand(query.scope(), Set.of(), query.actorUserRole());
    return source
        .fetch(command)
        .flatMap(result -> toCompleteSet(query, result))
        .flatMap(metadata -> cache.write(metadata).thenReturn(metadata));
  }

  private Mono<ResolvedGeneralMetadataSet> toCompleteSet(
      ResolveGeneralMetadataQuery query, FetchGeneralMetadataResult result) {
    if (result.resolutionSource().isEmpty()) {
      return Mono.error(new MissingRequiredGeneralMetadataException());
    }
    var metadata =
        ResolvedGeneralMetadataSet.normalize(
            query.scope(), result.resolutionSource().orElseThrow(), result.entries());
    if (!containsAll(metadata, query)) {
      return Mono.error(new MissingRequiredGeneralMetadataException());
    }
    return Mono.just(metadata);
  }

  private boolean containsAll(
      ResolvedGeneralMetadataSet metadata, ResolveGeneralMetadataQuery query) {
    return query.requiredKeys().stream().allMatch(key -> metadata.find(key).isPresent());
  }
}
