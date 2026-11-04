package com.bct.ngtpa.apiservice.application.usecase.accountsummary;

import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryCurrencyCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Reads and writes the account-summary currency-selection cache entry in the authenticated
 * session, per docs/refactor/3-account-endpoints-restructure/8-investgl-curr-required-addendum.md
 * D-2, mirroring {@code FundComparisonOptionsCacheService}'s read/write/Wire shape exactly.
 *
 * <p>Read is a plain session load and decrypt, not {@code loadAndTouch}: the portal access web
 * filter already touches the session on every authenticated request, so touching again here would
 * add a Redis write and widen the documented overwrite race. Every failure is a cache miss, never
 * an error.
 *
 * <p>Write replaces only this feature's {@code featureState} entry through the generic
 * revision-checked {@link AuthenticatedSessionConcurrencyService}. A write that is not applied, or
 * fails, is logged and skipped; it never fails the caller.
 *
 * <p>Logs carry the request id and a reason only; {@code policyNo} and {@code accountRef} are
 * never logged. Not a Spring bean by annotation (application layer stays framework-free).
 */
public final class AccountSummaryCurrencyCacheService {

  static final String FEATURE_KEY = "account-summary.currency";

  private static final Logger LOG =
      LoggerFactory.getLogger(AccountSummaryCurrencyCacheService.class);

  private final AuthenticatedSessionPort sessions;
  private final AuthenticatedSessionProtectorPort protector;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final TransientJsonPort json;
  private final Clock clock;

  public AccountSummaryCurrencyCacheService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionConcurrencyService concurrency,
      TransientJsonPort json,
      Clock clock) {
    this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    this.protector = Objects.requireNonNull(protector, "protector must not be null");
    this.concurrency = Objects.requireNonNull(concurrency, "concurrency must not be null");
    this.json = Objects.requireNonNull(json, "json must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /** Returns the cached entry for this account, or empty on any kind of miss. */
  public Mono<Optional<AccountSummaryCurrencyCacheEntry>> read(
      AuthenticatedSessionId sessionId, String accountRef, String policyNo, String requestId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    return sessions
        .load(new LoadAuthenticatedSessionCommand(sessionId, requestId))
        .map(
            stored ->
                stored
                    .map(envelope -> evaluate(envelope, accountRef, policyNo, requestId))
                    .orElseGet(() -> miss("session-missing", requestId)))
        .onErrorResume(
            error -> {
              LOG.warn(
                  "event=account-summary.currency-cache.miss reason=failed exceptionType={}"
                      + " requestId={}",
                  error.getClass().getSimpleName(),
                  requestId);
              return Mono.just(Optional.empty());
            });
  }

  /** Stores the entry for this session. Never errors; a skipped write is logged with a reason. */
  public Mono<Void> write(
      AuthenticatedSessionId sessionId, AccountSummaryCurrencyCacheEntry entry, String requestId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(entry, "entry must not be null");
    return Mono.fromCallable(() -> json.write(Wire.from(entry)))
        .flatMap(
            encoded ->
                concurrency.updateIfUsable(
                    sessionId,
                    session -> session.withFeatureState(FEATURE_KEY, encoded),
                    requestId))
        .doOnNext(result -> logWriteOutcome(result, requestId))
        .onErrorResume(
            error -> {
              LOG.warn(
                  "event=account-summary.currency-cache.write.skipped reason=failed"
                      + " exceptionType={} requestId={}",
                  error.getClass().getSimpleName(),
                  requestId);
              return Mono.empty();
            })
        .then();
  }

  private Optional<AccountSummaryCurrencyCacheEntry> evaluate(
      ProtectedAuthenticatedSession envelope,
      String accountRef,
      String policyNo,
      String requestId) {
    AuthenticatedSession session;
    try {
      session = protector.unprotect(envelope);
    } catch (RuntimeException exception) {
      return miss("session-unreadable", requestId);
    }
    if (!session.isUsable(clock.instant())) {
      return miss("session-unusable", requestId);
    }
    String encoded = session.featureState().get(FEATURE_KEY);
    if (encoded == null) {
      return miss("absent", requestId);
    }
    AccountSummaryCurrencyCacheEntry entry;
    try {
      entry = json.read(encoded, Wire.class).toEntry();
    } catch (RuntimeException exception) {
      return miss("unreadable", requestId);
    }
    if (!entry.belongsTo(accountRef, policyNo)) {
      return miss("account-mismatch", requestId);
    }
    LOG.info("event=account-summary.currency-cache.hit requestId={}", requestId);
    return Optional.of(entry);
  }

  private static Optional<AccountSummaryCurrencyCacheEntry> miss(String reason, String requestId) {
    LOG.info(
        "event=account-summary.currency-cache.miss reason={} requestId={}", reason, requestId);
    return Optional.empty();
  }

  private static void logWriteOutcome(
      AuthenticatedSessionConcurrencyResult result, String requestId) {
    if (result.outcome() == AuthenticatedSessionConcurrencyOutcome.APPLIED) {
      LOG.info("event=account-summary.currency-cache.write.applied requestId={}", requestId);
      return;
    }
    if (LOG.isWarnEnabled()) {
      LOG.warn(
          "event=account-summary.currency-cache.write.skipped reason={} requestId={}",
          skipReason(result.outcome()),
          requestId);
    }
  }

  private static String skipReason(AuthenticatedSessionConcurrencyOutcome outcome) {
    return switch (outcome) {
      case REVISION_CONFLICT_EXHAUSTED -> "conflict";
      case SESSION_NOT_FOUND -> "session-missing";
      case SESSION_NOT_USABLE -> "session-not-usable";
      default -> "failed";
    };
  }

  /** Stored JSON shape; unknown properties fail decoding and therefore become a cache miss. */
  private record Wire(String accountRef, String policyNo, List<String> availableCurrencies) {
    static Wire from(AccountSummaryCurrencyCacheEntry entry) {
      return new Wire(entry.accountRef(), entry.policyNo(), entry.availableCurrencies());
    }

    AccountSummaryCurrencyCacheEntry toEntry() {
      return new AccountSummaryCurrencyCacheEntry(accountRef, policyNo, availableCurrencies);
    }
  }
}
