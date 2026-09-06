package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.VerifyLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpVerifyExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFailureClassifiers;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Parallel Login OTP Verify coordinator backed by shared idempotency. */
public final class SharedLoginOtpVerifyService implements VerifyLoginOtpUseCase {
  private final IdempotencyExecutor executor;
  private final LoginOtpVerifyProtectedOperation operation;
  private final LoginOtpVerifyExecutionBridge responses;
  private final LoginIdempotencyIdentityFactory identities;
  private final LoginFingerprintFactory fingerprints;
  private final Duration ttl;

  public SharedLoginOtpVerifyService(
      IdempotencyExecutor executor,
      LoginOtpVerifyProtectedOperation operation,
      LoginOtpVerifyExecutionBridge responses,
      LoginIdempotencyIdentityFactory identities,
      LoginFingerprintFactory fingerprints,
      Duration ttl) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.identities = Objects.requireNonNull(identities);
    this.fingerprints = Objects.requireNonNull(fingerprints);
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    this.ttl = ttl;
  }

  @Override
  public Mono<VerifyLoginOtpResult> verify(VerifyLoginOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                LoginIdempotencyAction.OTP_VERIFY,
                command.authFlowSessionId(),
                command.idempotencyKey()),
            fingerprints.otpVerify(),
            ttl,
            () ->
                operation
                    .verifyProtected(command)
                    .map(
                        outcome -> {
                          HydratedIdempotentResponse response =
                              responses.toTransientResponse(outcome.result());
                          return outcome.protectedReplayReference() == null
                              ? response
                              : response.withProtectedReplayReference(
                                  outcome.protectedReplayReference());
                        }),
            LoginFailureClassifiers.possibleSideEffect(),
            responses::capture,
            snapshot -> responses.hydrate(snapshot, command.correlationId()));
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private VerifyLoginOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS,
          KEY_REUSED_WITH_DIFFERENT_REQUEST,
          FAILED_EXISTING,
          UNKNOWN_OUTCOME_EXISTING ->
          systemFailed();
    };
  }

  private VerifyLoginOtpResult systemFailed() {
    return new VerifyLoginOtpResult(
        VerifyLoginOtpStatus.SYSTEM_FAILED, null, false, List.of(), null, null);
  }
}
