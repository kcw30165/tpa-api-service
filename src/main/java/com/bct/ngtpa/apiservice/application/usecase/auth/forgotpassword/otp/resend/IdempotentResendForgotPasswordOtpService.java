package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend.ForgotPasswordOtpResendExecutionBridge;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/** Thin OTP_RESEND integration over the repository-wide shared idempotency executor. */
public final class IdempotentResendForgotPasswordOtpService
    implements ResendForgotPasswordOtpUseCase {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(IdempotentResendForgotPasswordOtpService.class);

  private final IdempotencyExecutor executor;
  private final ForgotPasswordOtpResendProtectedOperation operation;
  private final ForgotPasswordOtpResendExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;
  private final Duration ttl;

  public IdempotentResendForgotPasswordOtpService(
      IdempotencyExecutor executor,
      ForgotPasswordOtpResendProtectedOperation operation,
      ForgotPasswordOtpResendExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy,
      Duration ttl) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.ttl = Objects.requireNonNull(ttl);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("OTP_RESEND idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<ResendForgotPasswordOtpResult> resend(ResendForgotPasswordOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                ForgotPasswordIdempotencyAction.OTP_RESEND,
                command.sessionId().value(),
                command.idempotencyKey()),
            policy.otpResend(),
            ttl,
            () -> executeAcquired(command),
            IdempotencyFailureClassifiers.possibleSideEffect(),
            response -> responses.capture(response, response.protectedReplayReference()),
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()))
        .doOnError(cause -> logFailure(command, cause))
        .onErrorReturn(simple(ResendForgotPasswordOtpStatus.SYSTEM_FAILED));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(ResendForgotPasswordOtpCommand command) {
    return operation
        .execute(command)
        .flatMap(
            execution -> {
              if (execution.result().status() == ResendForgotPasswordOtpStatus.SYSTEM_FAILED) {
                return Mono.error(
                    new IllegalStateException(
                        "Forgot Password OTP_RESEND protected operation failed"));
              }
              HydratedIdempotentResponse response =
                  responses.toTransientResponse(execution.result());
              return Mono.just(
                  execution.replayReference() == null
                      ? response
                      : response.withProtectedReplayReference(execution.replayReference()));
            });
  }

  private ResendForgotPasswordOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(ResendForgotPasswordOtpStatus.SYSTEM_FAILED);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          simple(ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED);
      case FAILED_EXISTING -> simple(ResendForgotPasswordOtpStatus.PREVIOUS_FAILED);
      case UNKNOWN_OUTCOME_EXISTING -> simple(ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME);
    };
  }

  private static void logFailure(ResendForgotPasswordOtpCommand command, Throwable cause) {
    LOGGER.error(
        "event=forgot-password.resend.failed requestId={} stage=idempotency exceptionType={}",
        command.requestId(),
        cause.getClass().getSimpleName());
  }

  private ResendForgotPasswordOtpResult simple(ResendForgotPasswordOtpStatus status) {
    return new ResendForgotPasswordOtpResult(status, null, null, null, null, null);
  }
}
