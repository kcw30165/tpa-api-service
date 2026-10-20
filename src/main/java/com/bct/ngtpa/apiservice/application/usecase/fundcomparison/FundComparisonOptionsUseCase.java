package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the TRPGetFundCmpOpt source port and, for the options endpoint, writes the fetched
 * options into the session cache (docs/design/13-fund-comparison/09-options-session-cache-change.md
 * D4, sub-task 5.3).
 *
 * <p>{@link #getOptions} only fetches. {@link #getOptionsAndCache} fetches, then writes the raw
 * time-range and fund codes for the given session and account. Caching never fails the request: a
 * missing session or an entry that cannot be built is logged and skipped, and {@link
 * FundComparisonOptionsCacheService#write} never errors.
 *
 * <p>Returns the raw, application-layer {@link RawFundComparisonOptionsData}; mapping to the public
 * response stays in the controller (script 047). Not annotated with a Spring stereotype (application
 * layer stays framework-free); wired from {@code FundComparisonOptionsApplicationConfig}.
 */
public final class FundComparisonOptionsUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(FundComparisonOptionsUseCase.class);

  private final FundComparisonOptionsSourcePort sourcePort;
  private final FundComparisonOptionsCacheService cache;

  public FundComparisonOptionsUseCase(
      FundComparisonOptionsSourcePort sourcePort, FundComparisonOptionsCacheService cache) {
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
  }

  public Mono<RawFundComparisonOptionsData> getOptions(FetchFundComparisonOptionsCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    return sourcePort
        .fetchOptions(command)
        .onErrorMap(
            exception ->
                exception instanceof FundComparisonRetrievalException
                    ? exception
                    : FundComparisonRetrievalException.systemFailure());
  }

  /** Fetches the options and stores them in the session cache; caching never fails the call. */
  public Mono<RawFundComparisonOptionsData> getOptionsAndCache(
      FetchFundComparisonOptionsCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return getOptions(command)
        .flatMap(
            raw -> cacheIfPossible(raw, command, sessionId, accountRef, requestId).thenReturn(raw));
  }

  private Mono<Void> cacheIfPossible(
      RawFundComparisonOptionsData raw,
      FetchFundComparisonOptionsCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    if (sessionId.isEmpty()) {
      LOG.info(
          "event=fund-comparison.options-cache.write.skipped reason=no-session requestId={}",
          requestId);
      return Mono.empty();
    }
    FundComparisonOptionsCacheEntry entry;
    try {
      entry = FundComparisonOptionsCacheEntry.of(accountRef, command.policyNo(), raw);
    } catch (RuntimeException exception) {
      LOG.warn(
          "event=fund-comparison.options-cache.write.skipped reason=failed exceptionType={} requestId={}",
          exception.getClass().getSimpleName(),
          requestId);
      return Mono.empty();
    }
    return cache.write(sessionId.get(), entry, requestId);
  }
}
