package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginCredentialIdempotencyCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Coordinates protected response replay with the credential idempotency record. */
public final class LoginStartReplayCoordinator {
  private static final String OUTCOME_STARTED = "STARTED";
  private final LoginStartReplayPort replayPort;
  private final LoginStartReplayProtectorPort protector;
  private final LoginStartProtectedReplayCommitter protectedReplayCommitter;
  private final LoginCredentialIdempotencyCoordinator idempotency;
  private final Clock clock;

  public LoginStartReplayCoordinator(
      LoginStartReplayPort replayPort,
      LoginStartReplayProtectorPort protector,
      LoginStartReplayCommitRefGenerator refs,
      LoginCredentialIdempotencyCoordinator idempotency,
      Clock clock) {
    this.replayPort = Objects.requireNonNull(replayPort);
    this.protector = Objects.requireNonNull(protector);
    this.protectedReplayCommitter =
        new LoginStartProtectedReplayCommitter(replayPort, protector, refs, clock);
    this.idempotency = Objects.requireNonNull(idempotency);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<StartLoginResult> commit(
      LoginIdempotencyKey key, LoginCredentialRequestHash hash, StartLoginResult result) {
    return protectedReplayCommitter
        .commit(result, idempotency.ttl())
        .flatMap(
            committed ->
                idempotency
                    .complete(
                        key,
                        hash,
                        new LoginIdempotencyReplaySnapshot(
                            OUTCOME_STARTED, committed.replayCommitRef()))
                    .thenReturn(committed))
        .onErrorResume(
            error ->
                idempotency
                    .markUnknown(key, hash)
                    .onErrorResume(markError -> Mono.empty())
                    .then(Mono.error(error)));
  }

  public Mono<StartLoginResult> replay(LoginIdempotencyReplaySnapshot snapshot) {
    if (snapshot == null
        || !OUTCOME_STARTED.equals(snapshot.outcomeCode())
        || snapshot.opaqueCommitRef() == null) {
      return Mono.just(unknown());
    }
    LoginStartReplayCommitRef ref;
    try {
      ref = new LoginStartReplayCommitRef(snapshot.opaqueCommitRef());
    } catch (IllegalArgumentException exception) {
      return Mono.just(unknown());
    }
    return replayPort
        .load(ref)
        .map(
            optional ->
                optional
                    .map(protector::unprotect)
                    .filter(payload -> payload.expiresAt().isAfter(clock.instant()))
                    .map(this::toReplayResult)
                    .orElseGet(this::unknown));
  }

  public Mono<Void> markRejected(
      LoginIdempotencyKey key, LoginCredentialRequestHash hash, StartLoginStatus status) {
    return idempotency
        .markFailed(key, hash, "login.credentials." + status.name().toLowerCase())
        .then();
  }

  public Mono<Void> markUnknown(LoginIdempotencyKey key, LoginCredentialRequestHash hash) {
    return idempotency.markUnknown(key, hash).then();
  }

  private StartLoginResult toReplayResult(LoginStartReplayPayload payload) {
    long remaining = Duration.between(clock.instant(), payload.expiresAt()).toSeconds();
    if (remaining <= 0) return unknown();
    return new StartLoginResult(
        StartLoginStatus.COMPLETED_REPLAY,
        payload.authFlowSessionId(),
        payload.csrfToken(),
        Math.toIntExact(Math.min(remaining, payload.expiresInSeconds())),
        payload.destinations(),
        null,
        null);
  }

  private StartLoginResult unknown() {
    return new StartLoginResult(
        StartLoginStatus.UNKNOWN_OUTCOME, null, null, null, List.of(), null, null);
  }
}
