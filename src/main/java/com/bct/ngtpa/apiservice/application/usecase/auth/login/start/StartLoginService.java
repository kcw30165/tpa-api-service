package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginCredentialPolicyService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.LoginStartReplayCoordinator;
import java.util.*;
import reactor.core.publisher.Mono;

public final class StartLoginService implements StartLoginUseCase {
  private final LoginCredentialPolicyService policy;
  private final LoginCredentialRequestHasher hasher;
  private final LoginCredentialIdempotencyCoordinator idempotency;
  private final LoginStartProtectedOperation protectedOperation;
  private final LoginStartReplayCoordinator replayCoordinator;

  public StartLoginService(
      LoginCredentialPolicyService policy,
      LoginCredentialRequestHasher hasher,
      LoginCredentialIdempotencyCoordinator idempotency,
      LoginStartProtectedOperation protectedOperation,
      LoginStartReplayCoordinator replayCoordinator) {
    this.policy = Objects.requireNonNull(policy);
    this.hasher = Objects.requireNonNull(hasher);
    this.idempotency = Objects.requireNonNull(idempotency);
    this.protectedOperation = Objects.requireNonNull(protectedOperation);
    this.replayCoordinator = Objects.requireNonNull(replayCoordinator);
  }

  public Mono<StartLoginResult> start(StartLoginCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    CredentialAuthenticationCommand normalized =
        policy.validateAndNormalize(
            new CredentialAuthenticationCommand(
                command.username(),
                command.password(),
                command.acceptLanguage(),
                command.correlationId(),
                ActorUserRole.ROLE_USER_MBR));
    LoginIdempotencyKey key = new LoginIdempotencyKey(command.idempotencyKey());
    LoginCredentialRequestHash hash = hasher.hash(normalized);

    return idempotency
        .reserve(key, hash)
        .flatMap(
            reservation ->
                switch (reservation.outcome()) {
                  case STARTED ->
                      protectedOperation
                          .execute(normalized)
                          .flatMap(result -> finalizeStarted(key, hash, result))
                          .onErrorResume(
                              error ->
                                  replayCoordinator
                                      .markUnknown(key, hash)
                                      .onErrorResume(markError -> Mono.empty())
                                      .then(Mono.error(error)));
                  case COMPLETED_REPLAY ->
                      replayCoordinator.replay(reservation.record().replaySnapshot());
                  case DUPLICATE_IN_PROGRESS ->
                      Mono.just(simple(StartLoginStatus.DUPLICATE_IN_PROGRESS, null));
                  case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                      Mono.just(simple(StartLoginStatus.KEY_REUSED, null));
                  case FAILED_EXISTING -> Mono.just(simple(StartLoginStatus.FAILED_EXISTING, null));
                  case UNKNOWN_OUTCOME_EXISTING ->
                      Mono.just(simple(StartLoginStatus.UNKNOWN_OUTCOME, null));
                });
  }

  private Mono<StartLoginResult> finalizeStarted(
      LoginIdempotencyKey key, LoginCredentialRequestHash hash, StartLoginResult result) {
    if (result.status() == StartLoginStatus.STARTED) {
      return replayCoordinator.commit(key, hash, result);
    }
    if (result.status() == StartLoginStatus.UNAVAILABLE) {
      return replayCoordinator.markUnknown(key, hash).thenReturn(result);
    }
    return replayCoordinator.markRejected(key, hash, result.status()).thenReturn(result);
  }

  private StartLoginResult simple(StartLoginStatus s, String ref) {
    return new StartLoginResult(s, null, null, null, List.of(), null, ref);
  }
}
