package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceModeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceModeSourcePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Fetches TRPGetFundPriceMode via {@link FundPriceModeSourcePort} (the port, never the concrete
 * adapter) and unconditionally caches the result onto the authenticated session, per
 * fund-price-session-contract.md's "No hidden refresh": the mode endpoint always calls
 * TRPGetFundPriceMode and always overwrites cached metadata -- there is no conditional
 * skip-if-fresh path.
 *
 * <p>Deliberately NOT annotated with a Spring stereotype: the application layer must not depend on
 * Spring Framework (CleanArchitectureDependencyTest.applicationDoesNotDependOnSpring). This class
 * is wired as a bean explicitly from {@code config.fundprice.FundPriceModeApplicationConfig}.
 */
public final class FundPriceModeUseCase {

  private final FundPriceModeSourcePort sourcePort;
  private final AuthenticatedSessionConcurrencyService concurrencyService;

  public FundPriceModeUseCase(
      FundPriceModeSourcePort sourcePort,
      AuthenticatedSessionConcurrencyService concurrencyService) {
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.concurrencyService =
        Objects.requireNonNull(concurrencyService, "concurrencyService must not be null");
  }

  public Mono<FundPriceModeUseCaseResult> fetchAndCacheMode(
      AuthenticatedSessionId authenticatedSessionId,
      FetchFundPriceModeCommand command,
      String correlationId) {
    Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
    Objects.requireNonNull(command, "command must not be null");

    return sourcePort
        .fetchMode(command)
        .flatMap(
            raw ->
                concurrencyService
                    .updateIfUsable(authenticatedSessionId, stampMetadata(raw), correlationId)
                    .map(FundPriceModeUseCase::toUseCaseResult));
  }

  private static java.util.function.UnaryOperator<AuthenticatedSession> stampMetadata(
      RawFundPriceModeData raw) {
    FundPriceModeSessionMetadata metadata =
        new FundPriceModeSessionMetadata(
            raw.mode(),
            raw.fundPriceType(),
            raw.availableFundPriceDates(),
            raw.maximumFundPriceHistoryMonths(),
            raw.maxRiskLevel());
    return current ->
        new AuthenticatedSession(
            current.authenticatedSessionId(),
            current.actorContext(),
            current.accounts(),
            current.status(),
            current.csrfTokenHash(),
            current.createdAt(),
            current.lastAccessedAt(),
            current.idleExpiresAt(),
            current.absoluteExpiresAt(),
            current.revision(),
            metadata);
  }

  private static FundPriceModeUseCaseResult toUseCaseResult(
      com.bct.ngtpa.apiservice.application.usecase.session.authenticated
              .AuthenticatedSessionConcurrencyResult
          result) {
    if (result.outcome() == AuthenticatedSessionConcurrencyOutcome.APPLIED) {
      return FundPriceModeUseCaseResult.applied(result.session().fundPriceModeMetadata());
    }
    return FundPriceModeUseCaseResult.systemFailure();
  }
}
