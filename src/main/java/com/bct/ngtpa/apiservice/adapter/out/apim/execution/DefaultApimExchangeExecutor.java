package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Generic profile-aware APIM envelope orchestration without feature business interpretation. */
@Service
public final class DefaultApimExchangeExecutor implements ApimExchangeExecutor {
  private final ApimWebClientFacade facade;
  private final ApimPayloadCryptoService cryptoService;
  private final ApimProperties properties;

  public DefaultApimExchangeExecutor(
      ApimWebClientFacade facade,
      ApimPayloadCryptoService cryptoService,
      ApimProperties properties) {
    this.facade = Objects.requireNonNull(facade, "facade must not be null");
    this.cryptoService = Objects.requireNonNull(cryptoService, "cryptoService must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
    Objects.requireNonNull(request, "request must not be null");
    return Mono.defer(
        () -> properties.getEncryption().isEnabled() ? postEncrypted(request) : postPlain(request));
  }

  private <R, D> Mono<ApimResponseEnvelope<D>> postPlain(ApimExchangeRequest<R, D> request) {
    return Mono.defer(
            () -> {
              R transformed =
                  cryptoService.encryptRequest(
                      request.path(), request.request(), request.requestType(), null);
              return facade.postSuccessfulExchange(
                  request.path(),
                  transformed,
                  request.databaseScope(),
                  request.credentialContext(),
                  request.retryEligibility());
            })
        .switchIfEmpty(Mono.error(new IllegalStateException("APIM successful exchange was empty")))
        .flatMap(
            exchange ->
                Mono.defer(
                    () -> {
                      validateCommon(exchange);
                      if (exchange.responseVerificationKey() != null) {
                        return Mono.error(
                            new IllegalStateException(
                                "Plain APIM exchange unexpectedly returned verification key"));
                      }
                      return Mono.just(
                          cryptoService.decryptResponseEnvelope(
                              request.path(),
                              exchange.responseBody(),
                              request.responseDataType(),
                              null));
                    }));
  }

  private <R, D> Mono<ApimResponseEnvelope<D>> postEncrypted(ApimExchangeRequest<R, D> request) {
    return Mono.defer(
            () ->
                facade.postEncryptedSuccessfulExchange(
                    request.path(),
                    (profile, attemptKey) ->
                        cryptoService.encryptRequest(
                            request.path(), request.request(), request.requestType(), attemptKey),
                    request.databaseScope(),
                    request.credentialContext(),
                    request.retryEligibility()))
        .switchIfEmpty(Mono.error(new IllegalStateException("APIM successful exchange was empty")))
        .flatMap(
            exchange ->
                Mono.defer(
                    () -> {
                      validateCommon(exchange);
                      if (exchange.responseVerificationKey() == null) {
                        return Mono.error(
                            new IllegalStateException(
                                "Encrypted APIM exchange missing verification key"));
                      }
                      return Mono.just(
                          cryptoService.decryptResponseEnvelope(
                              request.path(),
                              exchange.responseBody(),
                              request.responseDataType(),
                              exchange.responseVerificationKey()));
                    }));
  }

  private static void validateCommon(ApimSuccessfulExchange exchange) {
    if (exchange == null) {
      throw new IllegalStateException("APIM successful exchange metadata was null");
    }
    if (exchange.profile() == null) {
      throw new IllegalStateException("APIM successful exchange profile was missing");
    }
  }
}
