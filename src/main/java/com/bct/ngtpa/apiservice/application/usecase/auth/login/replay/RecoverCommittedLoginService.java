package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class RecoverCommittedLoginService
    implements RecoverCommittedLoginUseCase, RecoverCommittedLoginByHandoffUseCase {
  private final AuthenticatedLoginHandoffPort handoffs;
  private final AuthenticatedLoginReplaySessionPort replaySessions;
  private final AuthenticatedSessionProtectorPort protector;
  private final CsrfTokenGenerator csrfTokens;
  private final CsrfTokenHasher csrfHasher;
  private final Clock clock;

  public RecoverCommittedLoginService(
      AuthenticatedLoginHandoffPort handoffs,
      AuthenticatedLoginReplaySessionPort replaySessions,
      AuthenticatedSessionProtectorPort protector,
      CsrfTokenGenerator csrfTokens,
      CsrfTokenHasher csrfHasher,
      Clock clock) {
    this.handoffs = Objects.requireNonNull(handoffs);
    this.replaySessions = Objects.requireNonNull(replaySessions);
    this.protector = Objects.requireNonNull(protector);
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
    var replacement =
        new AuthenticatedSession(
            session.authenticatedSessionId(),
            session.actorContext(),
            session.accounts(),
            session.status(),
            replacementHash,
            session.createdAt(),
            session.lastAccessedAt(),
            session.idleExpiresAt(),
            session.absoluteExpiresAt());
    Instant expiry =
        session.idleExpiresAt().isBefore(session.absoluteExpiresAt())
            ? session.idleExpiresAt()
            : session.absoluteExpiresAt();
    Duration ttl = Duration.between(now, expiry);
    if (ttl.isZero() || ttl.isNegative()) return Mono.just(RecoverCommittedLoginResult.failed());
    return replaySessions
        .rotateCsrfHashIfCurrent(
            new RotateAuthenticatedSessionCsrfHashCommand(
                session.authenticatedSessionId(),
                expected,
                protector.protect(replacement),
                session.csrfTokenHash(),
                replacementHash,
                ttl,
                correlationId))
        .filter(r -> r.status() == AuthenticatedSessionCsrfRotationStatus.ROTATED)
        .map(
            ignored ->
                new RecoverCommittedLoginResult(
                    true,
                    reconstructLoginAccounts(session.accounts()),
                    session.authenticatedSessionId(),
                    raw))
        .switchIfEmpty(Mono.just(RecoverCommittedLoginResult.failed()));
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
