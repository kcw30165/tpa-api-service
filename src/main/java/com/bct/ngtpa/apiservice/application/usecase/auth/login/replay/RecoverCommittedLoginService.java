package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import reactor.core.publisher.Mono;

/**
 * Recovers an already committed authenticated Login. The authenticated session is read through the
 * replay port; only its CSRF hash is replaced, through the revision-based compare-and-set of {@link
 * AuthenticatedSessionConcurrencyService}. The raw token is released only after the write applied.
 */
public final class RecoverCommittedLoginService
    implements RecoverCommittedLoginUseCase, RecoverCommittedLoginByHandoffUseCase {
  private final AuthenticatedLoginHandoffPort handoffs;
  private final AuthenticatedLoginReplaySessionPort replaySessions;
  private final AuthenticatedSessionProtectorPort protector;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final CsrfTokenGenerator csrfTokens;
  private final CsrfTokenHasher csrfHasher;
  private final Clock clock;

  public RecoverCommittedLoginService(
      AuthenticatedLoginHandoffPort handoffs,
      AuthenticatedLoginReplaySessionPort replaySessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionConcurrencyService concurrency,
      CsrfTokenGenerator csrfTokens,
      CsrfTokenHasher csrfHasher,
      Clock clock) {
    this.handoffs = Objects.requireNonNull(handoffs);
    this.replaySessions = Objects.requireNonNull(replaySessions);
    this.protector = Objects.requireNonNull(protector);
    this.concurrency = Objects.requireNonNull(concurrency);
    this.csrfTokens = Objects.requireNonNull(csrfTokens);
    this.csrfHasher = Objects.requireNonNull(csrfHasher);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<RecoverCommittedLoginResult> recover(RecoverCommittedLoginCommand command) {
    var proof = command.proof();
    return handoffs
        .loadByHandoff(proof.handoffReference())
        .filter(handoff -> handoff.operationReference().equals(proof.operationReference()))
        .filter(handoff -> handoff.reservationHash().equals(proof.reservationHash()))
        .flatMap(handoff -> recover(handoff, command.correlationId()))
        .switchIfEmpty(Mono.just(RecoverCommittedLoginResult.failed()))
        .onErrorReturn(RecoverCommittedLoginResult.failed());
  }

  @Override
  public Mono<RecoverCommittedLoginResult> recover(RecoverCommittedLoginByHandoffCommand command) {
    return handoffs
        .loadByHandoff(command.handoffReference())
        .flatMap(handoff -> recover(handoff, command.correlationId()))
        .switchIfEmpty(Mono.just(RecoverCommittedLoginResult.failed()))
        .onErrorReturn(RecoverCommittedLoginResult.failed());
  }

  private Mono<RecoverCommittedLoginResult> recover(
      AuthenticatedLoginHandoff handoff, String correlationId) {
    if (handoff.status() != AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED
        || handoff.authenticatedSessionId() == null
        || handoff.cleanupProof() == null
        || handoff.cleanupProof().isBlank()
        || !clock.instant().isBefore(handoff.expiresAt())) {
      return Mono.just(RecoverCommittedLoginResult.failed());
    }
    return replaySessions
        .loadForReplay(
            new LoadAuthenticatedSessionForReplayCommand(
                handoff.authenticatedSessionId(), correlationId))
        .flatMap(
            optional ->
                optional
                    .map(envelope -> rotate(envelope, handoff, correlationId))
                    .orElseGet(() -> Mono.just(RecoverCommittedLoginResult.failed())));
  }

  private Mono<RecoverCommittedLoginResult> rotate(
      ProtectedAuthenticatedSession expected,
      AuthenticatedLoginHandoff handoff,
      String correlationId) {
    AuthenticatedSession session = protector.unprotect(expected);
    Instant now = clock.instant();
    if (!session.authenticatedSessionId().equals(handoff.authenticatedSessionId())
        || !session.isUsable(now)) return Mono.just(RecoverCommittedLoginResult.failed());
    String raw = csrfTokens.generate();
    if (raw == null || raw.isBlank()) return Mono.just(RecoverCommittedLoginResult.failed());
    String replacementHash = csrfHasher.hash(raw);
    return concurrency
        .updateIfUsable(
            session.authenticatedSessionId(), withCsrfHash(replacementHash), correlationId)
        .filter(result -> result.outcome() == AuthenticatedSessionConcurrencyOutcome.APPLIED)
        .map(
            result ->
                new RecoverCommittedLoginResult(
                    true,
                    reconstructLoginAccounts(result.session().accounts()),
                    result.session().authenticatedSessionId(),
                    raw))
        .switchIfEmpty(Mono.just(RecoverCommittedLoginResult.failed()));
  }

  private static UnaryOperator<AuthenticatedSession> withCsrfHash(String csrfTokenHash) {
    return session ->
        new AuthenticatedSession(
            session.authenticatedSessionId(),
            session.actorContext(),
            session.accounts(),
            session.status(),
            csrfTokenHash,
            session.createdAt(),
            session.lastAccessedAt(),
            session.idleExpiresAt(),
            session.absoluteExpiresAt(),
            session.revision(),
            session.fundPriceModeMetadata());
  }

  private List<LoginSessionAccount> reconstructLoginAccounts(
      List<AuthenticatedSessionAccount> accounts) {
    return accounts.stream()
        .map(
            a ->
                new LoginSessionAccount(
                    new LoginAccountRef(a.accountRef().value()),
                    a.accountEnv(),
                    a.policyNo(),
                    a.policyNo(),
                    a.certNo(),
                    a.certNo(),
                    a.trustCode(),
                    a.schemeType(),
                    a.termStatus(),
                    a.termCompletionDate()))
        .toList();
  }
}
