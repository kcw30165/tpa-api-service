package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

/** Generic profile-aware APIM envelope orchestration without feature business interpretation. */
@Service
public final class DefaultApimExchangeExecutor implements ApimExchangeExecutor {
  private static final JsonFactory STRICT_JSON =
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private final ApimWebClientFacade facade;
  private final ApimPayloadCryptoService cryptoService;
  private final ApimProperties properties;
  private final ApimExchangeOutcomeLogger outcomeLogger;

  @Autowired
  public DefaultApimExchangeExecutor(
      ApimWebClientFacade facade,
      ApimPayloadCryptoService cryptoService,
      ApimProperties properties,
      ApimExchangeOutcomeLogger outcomeLogger) {
    this.facade = Objects.requireNonNull(facade, "facade must not be null");
    this.cryptoService = Objects.requireNonNull(cryptoService, "cryptoService must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.outcomeLogger = Objects.requireNonNull(outcomeLogger, "outcomeLogger must not be null");
  }

  /**
   * Legacy constructor retained for existing unit tests that construct this executor directly with
   * only the original three dependencies (see {@code DefaultApimExchangeExecutorTest}). Mirrors the
   * compatibility-constructor convention already used by {@link ApimWebClientFacade}: a real (not
   * no-op) {@link ApimExchangeOutcomeLogger} is built with a fresh {@link ObjectMapper} so outcome
   * logging still runs; it is simply not the Spring-managed singleton instance used in production.
   */
  public DefaultApimExchangeExecutor(
      ApimWebClientFacade facade,
      ApimPayloadCryptoService cryptoService,
      ApimProperties properties) {
    this(facade, cryptoService, properties, new ApimExchangeOutcomeLogger(new ObjectMapper()));
  }

  @Override
  public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
    Objects.requireNonNull(request, "request must not be null");
    return Mono.defer(
            () ->
                properties.getEncryption().isEnabled()
                    ? postEncrypted(request)
                    : postPlain(request))
        .doOnEach(signal -> logOutcome(request, signal));
  }

  private <D> void logOutcome(
      ApimExchangeRequest<?, D> request, Signal<ApimResponseEnvelope<D>> signal) {
    if (signal.isOnNext()) {
      String requestId =
          signal.getContextView().getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
      outcomeLogger.logSuccessOutcome(request, signal.get(), requestId);
    } else if (signal.isOnError()) {
      String requestId =
          signal.getContextView().getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
      outcomeLogger.logExchangeFailure(request, signal.getThrowable(), requestId);
    }
  }

  private <R, D> Mono<ApimResponseEnvelope<D>> postPlain(ApimExchangeRequest<R, D> request) {
    return Mono.defer(
            () -> {
              R transformed =
                  cryptoService.encryptRequest(
                      request.path(), request.request(), request.requestType(), null);
              return request.resubmissionPolicy() == ApimResubmissionPolicy.ALLOW_RECOVERY
                  ? facade.postSuccessfulExchange(
                      request.path(),
                      transformed,
                      request.databaseScope(),
                      request.credentialContext(),
                      request.retryEligibility(),
                      ApimResubmissionPolicy.ALLOW_RECOVERY,
                      request.incidentOwnership())
                  : facade.postSuccessfulExchange(
                      request.path(),
                      transformed,
                      request.databaseScope(),
                      request.credentialContext(),
                      request.retryEligibility(),
                      request.resubmissionPolicy(),
                      request.incidentOwnership());
            })
        .switchIfEmpty(Mono.error(new IllegalStateException("APIM successful exchange was empty")))
        .flatMap(
            exchange ->
                Mono.defer(
                    () -> {
                      validateCommon(exchange);
                      validateStrictResponse(request, exchange);
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
                request.resubmissionPolicy() == ApimResubmissionPolicy.ALLOW_RECOVERY
                    ? facade.postEncryptedSuccessfulExchange(
                        request.path(),
                        (profile, attemptKey) ->
                            cryptoService.encryptRequest(
                                request.path(),
                                request.request(),
                                request.requestType(),
                                attemptKey),
                        request.databaseScope(),
                        request.credentialContext(),
                        request.retryEligibility(),
                        ApimResubmissionPolicy.ALLOW_RECOVERY,
                        request.incidentOwnership())
                    : facade.postEncryptedSuccessfulExchange(
                        request.path(),
                        (profile, attemptKey) ->
                            cryptoService.encryptRequest(
                                request.path(),
                                request.request(),
                                request.requestType(),
                                attemptKey),
                        request.databaseScope(),
                        request.credentialContext(),
                        request.retryEligibility(),
                        request.resubmissionPolicy(),
                        request.incidentOwnership()))
        .switchIfEmpty(Mono.error(new IllegalStateException("APIM successful exchange was empty")))
        .flatMap(
            exchange ->
                Mono.defer(
                    () -> {
                      validateCommon(exchange);
                      validateStrictResponse(request, exchange);
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

  private static void validateStrictResponse(
      ApimExchangeRequest<?, ?> request, ApimSuccessfulExchange exchange) {
    if (request.resubmissionPolicy() != ApimResubmissionPolicy.NEVER) {
      return;
    }
    if (exchange.responseBody() == null) {
      throw new IllegalStateException("Invalid APIM response JSON");
    }
    try (var parser = STRICT_JSON.createParser(exchange.responseBody())) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException("Invalid APIM response JSON");
      }
      parser.skipChildren();
      if (parser.nextToken() != null) {
        throw new IllegalStateException("Invalid APIM response JSON");
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Invalid APIM response JSON");
    }
  }
}
