package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
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

/** Best-effort session cache for submit-time investment choice constraints. */
public final class InvestmentChoiceSnapshotCacheService {

  static final String FEATURE_KEY = "investment-choice.snapshot";

  private static final Logger LOG =
      LoggerFactory.getLogger(InvestmentChoiceSnapshotCacheService.class);

  private final AuthenticatedSessionPort sessions;
  private final AuthenticatedSessionProtectorPort protector;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final TransientJsonPort json;
  private final Clock clock;

  public InvestmentChoiceSnapshotCacheService(
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

  /** Returns the cached snapshot for this account, or empty on any kind of miss. */
  public Mono<Optional<InvestmentChoiceSnapshot>> read(
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
              LOG.info(
                  "event=investment-choice.snapshot-cache.miss reason=failed requestId={}",
                  requestId);
              return Mono.just(Optional.empty());
            });
  }

  /**
   * Stores the snapshot for this session. Never errors; a skipped write is logged with a reason.
   */
  public Mono<Void> write(
      AuthenticatedSessionId sessionId, InvestmentChoiceSnapshot snapshot, String requestId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    return Mono.fromCallable(() -> json.write(Wire.from(snapshot)))
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
                  "event=investment-choice.snapshot-cache.write.skipped reason=failed requestId={}",
                  requestId);
              return Mono.empty();
            })
        .then();
  }

  private Optional<InvestmentChoiceSnapshot> evaluate(
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
    InvestmentChoiceSnapshot snapshot;
    try {
      snapshot = json.read(encoded, Wire.class).toSnapshot();
    } catch (RuntimeException exception) {
      return miss("unreadable", requestId);
    }
    if (!snapshot.belongsTo(accountRef, policyNo)) {
      return miss("account-mismatch", requestId);
    }
    LOG.info("event=investment-choice.snapshot-cache.hit requestId={}", requestId);
    return Optional.of(snapshot);
  }

  private static Optional<InvestmentChoiceSnapshot> miss(String reason, String requestId) {
    LOG.info(
        "event=investment-choice.snapshot-cache.miss reason={} requestId={}", reason, requestId);
    return Optional.empty();
  }

  private static void logWriteOutcome(
      AuthenticatedSessionConcurrencyResult result, String requestId) {
    if (result.outcome() == AuthenticatedSessionConcurrencyOutcome.APPLIED) {
      LOG.info("event=investment-choice.snapshot-cache.write.applied requestId={}", requestId);
      return;
    }
    if (LOG.isWarnEnabled()) {
      LOG.warn(
          "event=investment-choice.snapshot-cache.write.skipped reason={} requestId={}",
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
  private record Wire(
      String accountRef,
      String policyNo,
      int intervalPcnt,
      List<InvestmentChoiceSnapshot.SwitchSource> sources,
      List<InvestmentChoiceSnapshot.Fund> funds,
      List<String> pendingRefNos) {

    static Wire from(InvestmentChoiceSnapshot snapshot) {
      return new Wire(
          snapshot.accountRef(),
          snapshot.policyNo(),
          snapshot.intervalPcnt(),
          snapshot.sources(),
          snapshot.funds(),
          snapshot.pendingRefNos());
    }

    InvestmentChoiceSnapshot toSnapshot() {
      return new InvestmentChoiceSnapshot(
          accountRef, policyNo, intervalPcnt, sources, funds, pendingRefNos);
    }
  }
}
