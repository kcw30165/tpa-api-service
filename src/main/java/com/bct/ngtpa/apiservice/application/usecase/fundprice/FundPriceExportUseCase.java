package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataPrecision;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolveGeneralMetadataQuery;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Fetches TRPGetFundPrice for the full cached history window -- NO FE date parameter, per
 * fund-price-api-contract.md Section 3 ("No FE date-range parameters"). Derives {@code fromDate =
 * referenceDate.minusMonths(maximumFundPriceHistoryMonths)} and {@code toDate = referenceDate},
 * and passes both directly as an explicit range via {@link FetchFundPriceRangeCommand} -- there
 * is no {@code mode} field and no adapter-side expansion anywhere on the export path (the
 * MONTHLY calendar-month expansion this Javadoc previously described was removed from {@code
 * ApimFundPriceSourceAdapter}; see fund-price-date-validation-contract.md).
 *
 * <p>Does NOT run {@link FundPriceRequestSupport#validateDate} -- there is no FE-supplied
 * candidate date to validate against the cached list.
 *
 * <p>Resolves {@code fund-pri}/{@code PRECISION} via {@link GeneralMetadataResolver}, mirroring
 * {@code GetAccountBalanceService}'s exact established pattern (confirmed real precedent, scripts
 * 136/137): reference date first, then metadata, then the price-range fetch, then assembly.
 * Neither the controller nor {@code FundPriceWorkbookExporter} ever resolves metadata directly --
 * the resolved precision is carried forward on {@link FundPriceExportResult}.
 */
public final class FundPriceExportUseCase {

  private static final GeneralMetadataKey UNIT_PRICE_PRECISION_KEY =
      new GeneralMetadataKey(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION);

  private final FundPriceRequestSupport requestSupport;
  private final FundPriceSourcePort sourcePort;
  private final ReferenceDatePort referenceDatePort;
  private final GeneralMetadataResolver generalMetadataResolver;

  public FundPriceExportUseCase(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle,
      FundPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver) {
    this.requestSupport = new FundPriceRequestSupport(lifecycle);
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.referenceDatePort =
        Objects.requireNonNull(referenceDatePort, "referenceDatePort must not be null");
    this.generalMetadataResolver =
        Objects.requireNonNull(generalMetadataResolver, "generalMetadataResolver must not be null");
  }

  public Mono<FundPriceExportUseCaseResult> export(
      AuthenticatedSessionId sessionId,
      String accountEnv,
      String policyNo,
      String trustCode,
      ActorUserRole actorUserRole) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return requestSupport
        .loadUsableSession(sessionId)
        .flatMap(
            maybeMetadata -> {
              if (maybeMetadata.isEmpty()) {
                return Mono.just(
                    FundPriceExportUseCaseResult.of(FundPriceUseCaseOutcome.SYSTEM_FAILURE));
              }
              FundPriceModeSessionMetadata metadata = maybeMetadata.get();
              return referenceDatePort
                  .resolveReferenceDate(accountEnv)
                  .flatMap(
                      referenceDate -> {
                        var metadataQuery =
                            new ResolveGeneralMetadataQuery(
                                new GeneralMetadataScope(accountEnv, trustCode, policyNo),
                                Set.of(UNIT_PRICE_PRECISION_KEY),
                                actorUserRole);
                        return generalMetadataResolver
                            .resolve(metadataQuery)
                            .flatMap(
                                resolvedMetadata -> {
                                  // NO mode reference anywhere in export's command construction --
                                  // confirmed design (Nick): export passes policyNo, fromDate,
                                  // toDate straight to APIM, bypassing
                                  // FetchFundPriceCommand/fetchPrice's mode-dependent
                                  // single-date expansion entirely.
                                  var command =
                                      new FetchFundPriceRangeCommand(
                                          policyNo,
                                          referenceDate.minusMonths(
                                              metadata.maximumFundPriceHistoryMonths()),
                                          referenceDate,
                                          accountEnv,
                                          actorUserRole);
                                  return sourcePort
                                      .fetchPriceRange(command)
                                      .map(
                                          raw ->
                                              assembleResult(raw, metadata, resolvedMetadata));
                                });
                      });
            });
  }

  private FundPriceExportUseCaseResult assembleResult(
      RawFundPriceData raw,
      FundPriceModeSessionMetadata metadata,
      ResolvedGeneralMetadataSet resolvedMetadata) {
    var rows =
        raw.rows().stream()
            .map(
                row ->
                    new FundPriceRowResult(
                        row.fundCode(),
                        row.fundName(),
                        row.chineseFundName(),
                        row.priceDate(),
                        row.fundCurrency(),
                        row.bidPrice(),
                        row.offerPrice(),
                        row.riskLevel()))
            .toList();
    boolean anyRiskLevel =
        rows.stream().anyMatch(r -> com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRiskLevels.isAssigned(r.riskLevel()));
    String maxRiskLevel =
        anyRiskLevel && com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRiskLevels.isAssigned(metadata.maxRiskLevel())
            ? metadata.maxRiskLevel()
            : null;
    BigInteger unitPricePrecision =
        GeneralMetadataPrecision.parse(
                resolvedMetadata.find(UNIT_PRICE_PRECISION_KEY).orElseThrow().value())
            .value();
    return FundPriceExportUseCaseResult.applied(
        new FundPriceExportResult(
            rows, metadata.fundPriceType(), maxRiskLevel, unitPricePrecision, metadata.mode()));
  }
}
