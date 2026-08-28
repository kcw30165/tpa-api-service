package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRow;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.FetchGeneralMetadataCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.FetchGeneralMetadataResult;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public final class ApimGeneralMetadataSourceAdapter implements GeneralMetadataSourcePort {
  static final String API_PATH = "/ws/NGTPA/v1/TRPGetGeneralMetaData";

  private static final Comparator<GeneralMetadataKey> KEY_ORDER =
      Comparator.comparing((GeneralMetadataKey key) -> key.name().name())
          .thenComparing(key -> key.type().name());

  private final ApimExchangeExecutor executor;
  private final GeneralMetadataApimContentNormalizer normalizer;

  public ApimGeneralMetadataSourceAdapter(ApimExchangeExecutor executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.normalizer = new GeneralMetadataApimContentNormalizer();
  }

  @Override
  public Mono<FetchGeneralMetadataResult> fetch(FetchGeneralMetadataCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var keys = command.requestedKeys().stream().sorted(KEY_ORDER).toList();
    if (keys.isEmpty()) {
      return execute(command, GeneralMetadataApimRequest.unfiltered(
          command.scope().accountEnv(), command.scope().trustCode(), command.scope().policyNo()));
    }
    return Flux.fromIterable(keys)
        .concatMap(key -> execute(command, GeneralMetadataApimRequest.targeted(
            command.scope().accountEnv(), command.scope().trustCode(), command.scope().policyNo(), key)))
        .collectList()
        .map(this::combine);
  }

  private Mono<FetchGeneralMetadataResult> execute(
      FetchGeneralMetadataCommand command, GeneralMetadataApimRequest request) {
    var scope = command.scope();
    var exchange = new ApimExchangeRequest<>(
        API_PATH,
        request,
        GeneralMetadataApimRequest.class,
        GeneralMetadataApimRow.class,
        ApimDatabaseScope.ACCOUNT_ENV,
        new ApimCredentialResolutionContext(command.actorUserRole(), scope.accountEnv()),
        ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return executor.postEnvelope(exchange)
        .map(response -> ApimResponseValidator.requireSuccessData(response))
        .map(rows -> normalizer.normalize(scope.policyNo(), rows));
  }

  private FetchGeneralMetadataResult combine(List<FetchGeneralMetadataResult> results) {
    Optional<GeneralMetadataResolutionSource> source = Optional.empty();
    var entries = new LinkedHashMap<GeneralMetadataKey, ResolvedGeneralMetadata>();
    for (var result : results) {
      if (source.isPresent() && result.resolutionSource().isPresent()
          && source.get() != result.resolutionSource().get()) {
        throw new IllegalArgumentException("Conflicting general metadata resolution sources");
      }
      if (source.isEmpty() && result.resolutionSource().isPresent()) source = result.resolutionSource();
      for (var entry : result.entries()) {
        var previous = entries.putIfAbsent(entry.key(), entry);
        if (previous != null && !previous.value().equals(entry.value()))
          throw new IllegalArgumentException("Conflicting resolved general metadata entry");
      }
    }
    return new FetchGeneralMetadataResult(source, new ArrayList<>(entries.values()));
  }
}
