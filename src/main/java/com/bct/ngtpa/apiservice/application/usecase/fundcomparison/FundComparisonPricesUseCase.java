package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonDateRange;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonDateRangeCalculator;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesValidatedRequest;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonSelectionMatcher;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Fund Comparison prices flow, per docs/design/13-fund-comparison/01-solution-design.md section 1.3
 * and 09-options-session-cache-change.md sections 9.4, 9.6 and 9.7.
 *
 * <ol>
 *   <li>Read the session options cache (no session id: treated as a miss).
 *   <li>A cached entry that matches the request is used as is: no options call.
 *   <li>A miss, or a cached entry that does not match, triggers exactly one options fetch, which
 *       also rewrites the cache ({@link FundComparisonOptionsUseCase#getOptionsAndCache}).
 *   <li>A request that does not match freshly fetched options is a retrieval failure (HTTP 503, Q1)
 *       logged at ERROR; TRPGetFundPrice is not called. An options failure is also a retrieval
 *       failure.
 *   <li>Otherwise the reference date is resolved, the inclusive window is calculated from the
 *       offered spelling of the time-range token, and TRPGetFundPrice is called with the offered
 *       spelling of every fund code, joined with commas and no spaces.
 * </ol>
 *
 * <p>Errors from the reference-date and price ports are wrapped as {@link
 * FundComparisonRetrievalException}; a {@link FundComparisonDateRangeCalculator} precondition
 * violation is left unwrapped (programming error). Logs carry the request id; policyNo and
 * accountRef are never logged. Not annotated with a Spring stereotype; wired from {@code
 * FundComparisonPricesApplicationConfig}.
 */
public final class FundComparisonPricesUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(FundComparisonPricesUseCase.class);

  private final FundComparisonPriceSourcePort sourcePort;
  private final ReferenceDatePort referenceDatePort;
  private final FundComparisonOptionsUseCase optionsUseCase;
  private final FundComparisonOptionsCacheService cache;

  public FundComparisonPricesUseCase(
      FundComparisonPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort,
      FundComparisonOptionsUseCase optionsUseCase,
      FundComparisonOptionsCacheService cache) {
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.referenceDatePort =
        Objects.requireNonNull(referenceDatePort, "referenceDatePort must not be null");
    this.optionsUseCase = Objects.requireNonNull(optionsUseCase, "optionsUseCase must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
  }

  public Mono<FundComparisonPricesResult> getPrices(
      FetchFundComparisonPricesUseCaseCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return resolveSelection(command, sessionId, accountRef, requestId)
        .flatMap(selection -> fetchPrices(withSelection(command, selection)));
  }

  /** Fetches and assembles prices for an already-matched selection. */
  Mono<FundComparisonPricesResult> fetchPrices(FetchFundComparisonPricesUseCaseCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .onErrorMap(FundComparisonPricesUseCase::toRetrievalException)
        .map(
            referenceDate ->
                FundComparisonDateRangeCalculator.calculate(referenceDate, command.timeRange()))
        .flatMap(dateRange -> fetchAndAssemble(command, dateRange));
  }

  private Mono<FundComparisonPricesValidatedRequest> resolveSelection(
      FetchFundComparisonPricesUseCaseCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Mono<Optional<FundComparisonOptionsCacheEntry>> cached =
        sessionId.isPresent()
            ? cache.read(sessionId.get(), accountRef, command.policyNo(), requestId)
            : Mono.fromSupplier(
                () -> {
                  LOG.info(
                      "event=fund-comparison.options-cache.miss reason=no-session requestId={}",
                      requestId);
                  return Optional.empty();
                });
    return cached.flatMap(
        entry -> {
          if (entry.isPresent()) {
            FundComparisonSelectionMatcher.Match match =
                FundComparisonSelectionMatcher.match(
                    entry.get().rawTimeRange(),
                    entry.get().fundCodes(),
                    command.fundCodes(),
                    command.timeRange());
            if (match.isMatched()) {
              return Mono.just(match.selection());
            }
            LOG.warn(
                "event=fund-comparison.options-cache.stale-refresh reason={} timeRange={} unlistedFundCodes={} requestId={}",
                match.reason(),
                command.timeRange(),
                match.unlistedFundCodes(),
                requestId);
          }
          return fetchAndMatch(command, sessionId, accountRef, requestId);
        });
  }

  private Mono<FundComparisonPricesValidatedRequest> fetchAndMatch(
      FetchFundComparisonPricesUseCaseCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    var optionsCommand =
        new FetchFundComparisonOptionsCommand(
            command.policyNo(), command.accountEnv(), command.actorUserRole());
    return optionsUseCase
        .getOptionsAndCache(optionsCommand, sessionId, accountRef, requestId)
        .flatMap(
            fresh -> {
              FundComparisonSelectionMatcher.Match match =
                  FundComparisonSelectionMatcher.match(
                      fresh.rawTimeRange(),
                      fresh.funds().stream()
                          .map(RawFundComparisonOptionsData.RawFundComparisonOption::fundCode)
                          .toList(),
                      command.fundCodes(),
                      command.timeRange());
              if (match.isMatched()) {
                return Mono.just(match.selection());
              }
              LOG.error(
                  "event=fund-comparison.membership.rejected reason={} timeRange={} unlistedFundCodes={} source=fresh requestId={}",
                  match.reason(),
                  command.timeRange(),
                  match.unlistedFundCodes(),
                  requestId);
              return Mono.error(FundComparisonRetrievalException.systemFailure());
            });
  }

  private Mono<FundComparisonPricesResult> fetchAndAssemble(
      FetchFundComparisonPricesUseCaseCommand command, FundComparisonDateRange dateRange) {
    String fundList = String.join(",", command.fundCodes());
    var portCommand =
        new FetchFundComparisonPricesCommand(
            command.policyNo(),
            dateRange.fromDate(),
            dateRange.toDate(),
            fundList,
            command.accountEnv(),
            command.actorUserRole());
    return sourcePort
        .fetchPrices(portCommand)
        .onErrorMap(FundComparisonPricesUseCase::toPricesException)
        .map(
            rawData ->
                new FundComparisonPricesResult(
                    dateRange.fromDate(), dateRange.toDate(), command.fundCodes(), rawData));
  }

  private static FetchFundComparisonPricesUseCaseCommand withSelection(
      FetchFundComparisonPricesUseCaseCommand command,
      FundComparisonPricesValidatedRequest selection) {
    return new FetchFundComparisonPricesUseCaseCommand(
        command.policyNo(),
        command.accountEnv(),
        command.actorUserRole(),
        selection.fundCodes(),
        selection.timeRange());
  }

  private static Throwable toRetrievalException(Throwable exception) {
    return exception instanceof FundComparisonRetrievalException
        ? exception
        : FundComparisonRetrievalException.systemFailure();
  }

  /**
   * Prices-fetch-specific error mapping (D6): a downstream err.tpa.invalid.date is a public
   * request-validation failure, not a retrieval failure, per
   * docs/design/13-fund-comparison/09-options-session-cache-change.md section 9.6.
   */
  private static Throwable toPricesException(Throwable exception) {
    if (exception instanceof FundPriceInvalidDateException) {
      return new FundComparisonRequestInvalidException(
          "downstream rejected the requested date range");
    }
    return toRetrievalException(exception);
  }
}
