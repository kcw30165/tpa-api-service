package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginOtpResendSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFailureClassifiers;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyIdentityFactory;
import java.time.Duration;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Parallel Login OTP Resend coordinator backed by shared idempotency. */
public final class SharedLoginOtpResendService implements ResendLoginOtpUseCase {
  private final IdempotencyExecutor executor;
  private final ResendLoginOtpUseCase operation;
  private final LoginOtpResendSharedExecutionBridge responses;
  private final LoginSharedIdempotencyIdentityFactory identities;
  private final LoginSharedFingerprintFactory fingerprints;
  private final Duration ttl;

  public SharedLoginOtpResendService(
      IdempotencyExecutor executor,
      ResendLoginOtpUseCase operation,
      LoginOtpResendSharedExecutionBridge responses,
      LoginSharedIdempotencyIdentityFactory identities,
      LoginSharedFingerprintFactory fingerprints,
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
  public Mono<ResendLoginOtpResult> resend(ResendLoginOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                LoginSharedIdempotencyAction.OTP_RESEND,
                command.authFlowSessionId(),
                command.idempotencyKey()),
            fingerprints.otpResend(command.authFlowSessionId()),
            ttl,
            () -> operation.resend(command).map(responses::toTransientResponse),
            LoginSharedFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private ResendLoginOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(ResendLoginOtpStatus.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST -> simple(ResendLoginOtpStatus.KEY_REUSED);
      case FAILED_EXISTING -> simple(ResendLoginOtpStatus.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING -> simple(ResendLoginOtpStatus.UNKNOWN_OUTCOME);
    };
  }

  private ResendLoginOtpResult simple(ResendLoginOtpStatus status) {
    return new ResendLoginOtpResult(status, null, null, null, null);
  }
}
