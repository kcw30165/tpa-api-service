package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpSendExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFailureClassifiers;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import java.time.Duration;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Parallel Login OTP Send coordinator backed by shared idempotency. */
public final class SharedLoginOtpSendService implements SendLoginOtpUseCase {
  private final IdempotencyExecutor executor;
  private final SendLoginOtpUseCase operation;
  private final LoginOtpSendExecutionBridge responses;
  private final LoginIdempotencyIdentityFactory identities;
  private final LoginFingerprintFactory fingerprints;
  private final Duration ttl;

  public SharedLoginOtpSendService(
      IdempotencyExecutor executor,
      SendLoginOtpUseCase operation,
      LoginOtpSendExecutionBridge responses,
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
  public Mono<SendLoginOtpResult> send(SendLoginOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                LoginIdempotencyAction.OTP_SEND,
                command.authFlowSessionId(),
                command.idempotencyKey()),
            fingerprints.otpSend(command.authFlowSessionId(), command.destinationId()),
            ttl,
            () -> operation.send(command).map(responses::toTransientResponse),
            LoginFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private SendLoginOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(SendLoginOtpStatus.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST -> simple(SendLoginOtpStatus.KEY_REUSED);
      case FAILED_EXISTING -> simple(SendLoginOtpStatus.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING -> simple(SendLoginOtpStatus.UNKNOWN_OUTCOME);
    };
  }

  private SendLoginOtpResult simple(SendLoginOtpStatus status) {
    return new SendLoginOtpResult(status, null, null, null, null);
  }
}
