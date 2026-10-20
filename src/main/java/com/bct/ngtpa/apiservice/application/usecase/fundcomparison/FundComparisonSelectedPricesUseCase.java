package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonSelection;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonSelectionMatch;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonSelectionMatcher;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Prices flow with membership validation, per
 * docs/design/13-fund-comparison/09-options-session-cache-change.md sections 9.4, 9.6 and 9.7.
 *
 * <ol>
 *   <li>Read the session options cache (no session id: treated as a miss).
 *   <li>A cached entry that matches the request is used as is: no options call.
 *   <li>A miss, or a cached entry that does not match, triggers exactly one options fetch, which
 *       also rewrites the cache ({@link FundComparisonOptionsUseCase#getOptionsAndCache}).
 *   <li>A request that does not match freshly fetched options is a retrieval failure (HTTP 503, Q1)
 *       logged at ERROR; TRPGetFundPrice is not called. An options failure is also a retrieval
 *       failure.
 *   <li>Otherwise prices are fetched with the offered spelling of every fund code and of the
 *       time-range token ({@link FundComparisonPricesUseCase}).
 * </ol>
 *
 * <p>Logs carry the request id; policyNo and accountRef are never logged. Not annotated with a
 * Spring stereotype; wired from {@code FundComparisonPricesApplicationConfig}.
 */
public final class FundComparisonSelectedPricesUseCase {

  private static final Logger LOG =
      LoggerFactory.getLogger(FundComparisonSelectedPricesUseCase.class);

  private final FundComparisonOptionsUseCase optionsUseCase;
  private final FundComparisonOptionsCacheService cache;
  private final FundComparisonPricesUseCase pricesUseCase;

  public FundComparisonSelectedPricesUseCase(
      FundComparisonOptionsUseCase optionsUseCase,
      FundComparisonOptionsCacheService cache,
      FundComparisonPricesUseCase pricesUseCase) {
    this.optionsUseCase = Objects.requireNonNull(optionsUseCase, "optionsUseCase must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.pricesUseCase = Objects.requireNonNull(pricesUseCase, "pricesUseCase must not be null");
  }

  public Mono<FundComparisonPricesResult> getPrices(
      FetchFundComparisonPricesUseCaseCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return resolveSelection(command, sessionId, accountRef, requestId)
        .flatMap(selection -> pricesUseCase.getPrices(withSelection(command, selection)));
  }

  private Mono<FundComparisonSelection> resolveSelection(
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
            FundComparisonSelectionMatch match =
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

  private Mono<FundComparisonSelection> fetchAndMatch(
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
              FundComparisonSelectionMatch match =
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

  private static FetchFundComparisonPricesUseCaseCommand withSelection(
      FetchFundComparisonPricesUseCaseCommand command, FundComparisonSelection selection) {
    return new FetchFundComparisonPricesUseCaseCommand(
        command.policyNo(),
        command.accountEnv(),
        command.actorUserRole(),
        selection.fundCodes(),
        selection.timeRange());
  }
}
