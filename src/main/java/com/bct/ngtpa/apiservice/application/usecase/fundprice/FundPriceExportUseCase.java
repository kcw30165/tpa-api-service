package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Fetches TRPGetFundPrice for the full cached history window -- NO FE date parameter, per
 * fund-price-api-contract.md Section 3 ("No FE date-range parameters"). Passes the authoritative
 * referenceDate itself as the command's date, with mode="MONTHLY" always, so
 * ApimFundPriceSourceAdapter's existing MONTHLY expansion derives the calendar-month range; the
 * confirmed maximumFundPriceHistoryMonths window beyond a single month is intentionally NOT
 * re-derived here, since the adapter's from-date/to-date expansion is a single-month operation by
 * design (mirrors TRPGetFundPriceMode's own month-at-a-time contract) -- export's full-history
 * behavior over multiple months, if required beyond a single calendar month, is a downstream
 * design question flagged for the web/controller stage, not resolved in this use case.
 *
 * <p>Does NOT run {@link FundPriceRequestSupport#validateDate} -- there is no FE-supplied
 * candidate date to validate against the cached list.
 */
public final class FundPriceExportUseCase {

  private final FundPriceRequestSupport requestSupport;
  private final FundPriceSourcePort sourcePort;
  private final ReferenceDatePort referenceDatePort;

  public FundPriceExportUseCase(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle,
      FundPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort) {
    this.requestSupport = new FundPriceRequestSupport(lifecycle);
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.referenceDatePort =
        Objects.requireNonNull(referenceDatePort, "referenceDatePort must not be null");
  }

  public Mono<FundPriceExportUseCaseResult> export(
      AuthenticatedSessionId sessionId,
      String accountEnv,
      String policyNo,
      ActorUserRole actorUserRole) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    return requestSupport
        .loadUsableSession(sessionId)
        .flatMap(
            sessionCheck -> {
              if (sessionCheck.outcome() != FundPriceRequestSupport.SessionCheckOutcome.USABLE) {
                return Mono.just(
                    FundPriceExportUseCaseResult.of(FundPriceUseCaseOutcome.SYSTEM_FAILURE));
              }
              FundPriceModeSessionMetadata metadata = sessionCheck.usable().metadata();
              return referenceDatePort
                  .resolveReferenceDate(accountEnv)
                  .flatMap(
                      referenceDate -> {
                        var command =
                            new FetchFundPriceCommand(
                                policyNo, referenceDate, metadata.mode(), accountEnv, actorUserRole);
                        return sourcePort
                            .fetchPrice(command)
                            .map(raw -> assembleResult(raw, metadata));
                      });
            });
  }

  private FundPriceExportUseCaseResult assembleResult(
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
    return FundPriceExportUseCaseResult.applied(
        new FundPriceExportResult(rows, metadata.fundPriceType(), maxRiskLevel));
  }
}
