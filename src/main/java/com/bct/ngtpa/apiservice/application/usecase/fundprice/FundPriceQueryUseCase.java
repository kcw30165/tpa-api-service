package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.time.LocalDate;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Fetches TRPGetFundPrice for an FE-supplied date, validated against cached
 * {@link FundPriceModeSessionMetadata} (range + exact membership in availableFundPriceDates).
 * Framework-free (no Spring annotation), matching {@code FundPriceModeUseCase}'s precedent.
 */
public final class FundPriceQueryUseCase {

  private final FundPriceRequestSupport requestSupport;
  private final FundPriceSourcePort sourcePort;
  private final ReferenceDatePort referenceDatePort;

  public FundPriceQueryUseCase(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle,
      FundPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort) {
    this.requestSupport = new FundPriceRequestSupport(lifecycle);
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.referenceDatePort =
        Objects.requireNonNull(referenceDatePort, "referenceDatePort must not be null");
  }

  public Mono<FundPriceQueryUseCaseResult> query(
      AuthenticatedSessionId sessionId,
      LocalDate date,
      String accountEnv,
      String policyNo,
      ActorUserRole actorUserRole) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(date, "date must not be null");

    return requestSupport
        .loadUsableSession(sessionId)
        .flatMap(
            maybeMetadata -> {
              if (maybeMetadata.isEmpty()) {
                return Mono.just(
                    FundPriceQueryUseCaseResult.of(FundPriceUseCaseOutcome.SYSTEM_FAILURE));
              }
              FundPriceModeSessionMetadata metadata = maybeMetadata.get();
              return referenceDatePort
                  .resolveReferenceDate(accountEnv)
                  .flatMap(
                      referenceDate -> {
                        var dateCheck = requestSupport.validateDate(metadata, referenceDate, date);
                        if (dateCheck == FundPriceRequestSupport.DateCheckOutcome.INVALID) {
                          return Mono.just(
                              FundPriceQueryUseCaseResult.of(FundPriceUseCaseOutcome.INVALID_DATE));
                        }
                        var command =
                            new FetchFundPriceCommand(
                                policyNo, date, metadata.mode(), accountEnv, actorUserRole);
                        return sourcePort
                            .fetchPrice(command)
                            .map(raw -> assembleResult(raw, metadata));
                      });
            });
  }

  private FundPriceQueryUseCaseResult assembleResult(
      RawFundPriceData raw, FundPriceModeSessionMetadata metadata) {
    var rows =
        raw.rows().stream()
            .map(
                row ->
                    new FundPriceRowResult(
                        row.fundCode(),
                        row.fundName(),
                        row.priceDate(),
                        row.fundCurrency(),
                        row.bidPrice(),
                        row.offerPrice(),
                        row.riskLevel()))
            .toList();
    boolean anyRiskLevel = rows.stream().anyMatch(r -> r.riskLevel() != null && !r.riskLevel().isEmpty());
    String maxRiskLevel = anyRiskLevel ? metadata.maxRiskLevel() : null;
    return FundPriceQueryUseCaseResult.applied(
        new FundPriceQueryResult(rows, metadata.fundPriceType(), maxRiskLevel));
  }
}
