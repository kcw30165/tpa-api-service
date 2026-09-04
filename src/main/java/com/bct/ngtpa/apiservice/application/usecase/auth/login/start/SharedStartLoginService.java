package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFailureClassifiers;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginStartSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginCredentialPolicyService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.LoginStartProtectedReplayCommitter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Parallel Login Start coordinator backed by shared idempotency. Not production-wired in P4-B2B.
 */
public final class SharedStartLoginService implements StartLoginUseCase {
  static final String ANONYMOUS_SCOPE = "login-start-anonymous-v1";
  private final LoginCredentialPolicyService policy;
  private final IdempotencyExecutor executor;
  private final LoginStartProtectedOperation operation;
  private final LoginStartProtectedReplayCommitter committer;
  private final LoginStartSharedExecutionBridge responses;
  private final LoginSharedIdempotencyIdentityFactory identities;
  private final LoginSharedFingerprintFactory fingerprints;
  private final Duration ttl;

  public SharedStartLoginService(
      LoginCredentialPolicyService policy,
      IdempotencyExecutor executor,
      LoginStartProtectedOperation operation,
      LoginStartProtectedReplayCommitter committer,
      LoginStartSharedExecutionBridge responses,
      LoginSharedIdempotencyIdentityFactory identities,
      LoginSharedFingerprintFactory fingerprints,
      Duration ttl) {
    this.policy = Objects.requireNonNull(policy);
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.committer = Objects.requireNonNull(committer);
    this.responses = Objects.requireNonNull(responses);
    this.identities = Objects.requireNonNull(identities);
    this.fingerprints = Objects.requireNonNull(fingerprints);
    if (ttl == null || ttl.isZero() || ttl.isNegative())
      throw new IllegalArgumentException("ttl must be positive");
    this.ttl = ttl;
  }

  @Override
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
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                LoginSharedIdempotencyAction.CREDENTIALS,
                ANONYMOUS_SCOPE,
                command.idempotencyKey()),
            fingerprints.credentials(normalized.username(), normalized.password()),
            ttl,
            () -> executeAcquired(normalized),
            LoginSharedFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor.execute(execution).map(r -> route(r.lifecycleOutcome(), r.terminalResponse()));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      CredentialAuthenticationCommand command) {
    return operation
        .execute(command)
        .flatMap(
            result -> {
              if (result.status() == StartLoginStatus.UNAVAILABLE) {
                return Mono.error(
                    new IllegalStateException("Login Start credential result is unavailable"));
              }
              if (result.status() == StartLoginStatus.STARTED) {
                return committer.commit(result, ttl).map(responses::toTransientResponse);
              }
              return Mono.just(responses.toTransientResponse(result));
            });
  }

  private StartLoginResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(StartLoginStatus.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST -> simple(StartLoginStatus.KEY_REUSED);
      case FAILED_EXISTING -> simple(StartLoginStatus.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING -> simple(StartLoginStatus.UNKNOWN_OUTCOME);
    };
  }

  private StartLoginResult simple(StartLoginStatus status) {
    return new StartLoginResult(status, null, null, null, List.of(), null, null);
  }
}
