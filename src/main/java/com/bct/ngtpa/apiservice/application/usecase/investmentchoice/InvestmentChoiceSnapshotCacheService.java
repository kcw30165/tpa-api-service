package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
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

  /** Returns the cached state for this account, or empty on any kind of miss. */
  public Mono<Optional<InvestmentChoiceSnapshotState>> readState(
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
   * Returns only editable NORMAL constraints; restricted states intentionally project to a miss.
   */
  public Mono<Optional<InvestmentChoiceSnapshot>> read(
      AuthenticatedSessionId sessionId, String accountRef, String policyNo, String requestId) {
    return readState(sessionId, accountRef, policyNo, requestId)
        .map(state -> state.flatMap(InvestmentChoiceSnapshotState::normalSnapshot));
  }

  /**
   * Stores the snapshot for this session. Never errors; a skipped write is logged with a reason.
   */
  public Mono<Void> write(
      AuthenticatedSessionId sessionId, InvestmentChoiceSnapshot snapshot, String requestId) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    return writeState(sessionId, InvestmentChoiceSnapshotState.normal(snapshot), requestId);
  }

  /** Stores restricted state and fails the GET if replacement was not applied. */
  public Mono<Void> writeRestrictedState(
      AuthenticatedSessionId sessionId, InvestmentChoiceSnapshotState state, String requestId) {
    Objects.requireNonNull(state, "state must not be null");
    if (state.scenario() == InvestmentChoiceScenario.NORMAL) {
      throw new IllegalArgumentException("Restricted state is required");
    }
    return writeState(sessionId, state, requestId, true);
  }

  public Mono<Void> writeState(
      AuthenticatedSessionId sessionId, InvestmentChoiceSnapshotState state, String requestId) {
    return writeState(sessionId, state, requestId, false);
  }

  private Mono<Void> writeState(
      AuthenticatedSessionId sessionId,
      InvestmentChoiceSnapshotState state,
      String requestId,
      boolean required) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(state, "state must not be null");
    return Mono.fromCallable(() -> Wire.encode(state, json))
        .flatMap(
            encoded ->
                concurrency.updateIfUsable(
                    sessionId,
                    session -> session.withFeatureState(FEATURE_KEY, encoded),
                    requestId))
        .flatMap(
            result -> {
              logWriteOutcome(result, requestId);
              if (required && result.outcome() != AuthenticatedSessionConcurrencyOutcome.APPLIED) {
                return Mono.error(new IllegalStateException("Restricted snapshot was not applied"));
              }
              return Mono.empty();
            })
        .onErrorResume(
            error -> {
              LOG.warn(
                  "event=investment-choice.snapshot-cache.write.skipped reason=failed requestId={}",
                  requestId);
              return required ? Mono.error(error) : Mono.empty();
            })
        .then();
  }

  private Optional<InvestmentChoiceSnapshotState> evaluate(
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
    InvestmentChoiceSnapshotState state;
    try {
      state = json.read(encoded, Wire.class).toState();
    } catch (RuntimeException exception) {
      return miss("unreadable", requestId);
    }
    if (!state.belongsTo(accountRef, policyNo)) {
      return miss("account-mismatch", requestId);
    }
    LOG.info("event=investment-choice.snapshot-cache.hit requestId={}", requestId);
    return Optional.of(state);
  }

  private static Optional<InvestmentChoiceSnapshotState> miss(String reason, String requestId) {
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
      String scenario,
      String accountRef,
      String policyNo,
      Integer intervalPcnt,
      List<InvestmentChoiceSnapshot.SwitchSource> sources,
      List<InvestmentChoiceSnapshot.Fund> funds,
      List<String> pendingRefNos) {

    InvestmentChoiceSnapshotState toState() {
      if (scenario == null || scenario.equals(InvestmentChoiceScenario.NORMAL.name())) {
        return InvestmentChoiceSnapshotState.normal(
            new InvestmentChoiceSnapshot(
                accountRef, policyNo, requiredInterval(), sources, funds, pendingRefNos));
      }
      InvestmentChoiceScenario restrictedScenario =
          switch (scenario) {
            case "FUNCTION_UNAVAILABLE" -> InvestmentChoiceScenario.FUNCTION_UNAVAILABLE;
            case "MEMBER_INELIGIBLE" -> InvestmentChoiceScenario.MEMBER_INELIGIBLE;
            default -> throw new IllegalArgumentException("Unknown stored scenario");
          };
      if (intervalPcnt != null || sources != null || funds != null || pendingRefNos != null) {
        throw new IllegalArgumentException("Restricted snapshot contains normal-only state");
      }
      return InvestmentChoiceSnapshotState.restricted(accountRef, policyNo, restrictedScenario);
    }

    private int requiredInterval() {
      if (intervalPcnt == null) {
        throw new IllegalArgumentException("NORMAL snapshot interval is required");
      }
      return intervalPcnt;
    }

    static String encode(InvestmentChoiceSnapshotState state, TransientJsonPort json) {
      if (state.normalSnapshot().isPresent()) {
        InvestmentChoiceSnapshot snapshot = state.normalSnapshot().orElseThrow();
        return json.write(
            new NormalWire(
                InvestmentChoiceScenario.NORMAL.name(),
                snapshot.accountRef(),
                snapshot.policyNo(),
                snapshot.intervalPcnt(),
                snapshot.sources(),
                snapshot.funds(),
                snapshot.pendingRefNos()));
      }
      return json.write(
          new RestrictedWire(state.scenario().name(), state.accountRef(), state.policyNo()));
    }
  }

  private record NormalWire(
      String scenario,
      String accountRef,
      String policyNo,
      int intervalPcnt,
      List<InvestmentChoiceSnapshot.SwitchSource> sources,
      List<InvestmentChoiceSnapshot.Fund> funds,
      List<String> pendingRefNos) {}

  private record RestrictedWire(String scenario, String accountRef, String policyNo) {}
}
