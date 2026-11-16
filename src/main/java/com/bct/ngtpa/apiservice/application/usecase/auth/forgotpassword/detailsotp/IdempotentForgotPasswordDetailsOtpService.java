package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpExecutionBridge;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Mono;

/** Thin DETAILS_AND_OTP integration over the repository-wide shared idempotency executor. */
public final class IdempotentForgotPasswordDetailsOtpService
    implements ProcessForgotPasswordDetailsOtpUseCase {
  private final IdempotencyExecutor executor;
  private final ForgotPasswordDetailsOtpProtectedOperation operation;
  private final ForgotPasswordDetailsOtpExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;
  private final FailureEventPort failureEventPort;
  private final ForgotPasswordDetailsValidator validator = new ForgotPasswordDetailsValidator();
  private final Duration ttl;

  public IdempotentForgotPasswordDetailsOtpService(
      IdempotencyExecutor executor,
      ForgotPasswordDetailsOtpProtectedOperation operation,
      ForgotPasswordDetailsOtpExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy,
      Duration ttl,
      FailureEventPort failureEventPort) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.failureEventPort = Objects.requireNonNull(failureEventPort);
    this.ttl = Objects.requireNonNull(ttl);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("DETAILS_AND_OTP idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<ForgotPasswordDetailsOtpResult> process(ForgotPasswordDetailsOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var validation = validator.validate(command.dateOfBirth(), command.email(), command.mobile());
    if (!validation.valid()) {
      return Mono.just(
          new ForgotPasswordDetailsOtpResult(
              ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED,
              null,
              0,
              0,
              validation.targets(), false));
    }
    var incidentPublished = new AtomicBoolean();
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
                command.sessionId().value(),
                command.idempotencyKey()),
            policy.detailsAndOtp(
                List.of(
                    command.dateOfBirth(),
                    Objects.toString(command.email(), ""),
                    Objects.toString(command.mobile(), ""))),
            ttl,
            () -> executeAcquired(command, incidentPublished),
            IdempotencyFailureClassifiers.possibleSideEffect(),
            response -> responses.capture(response, response.protectedReplayReference()),
            responses::hydrate);
    return executor
        .execute(execution)
        .map(
            result -> {
              ForgotPasswordDetailsOtpResult routed =
                  route(result.lifecycleOutcome(), result.terminalResponse());
              return result.lifecycleOutcome() == IdempotencyReservationOutcome.ACQUIRED
                      && incidentPublished.get()
                  ? routed.withIncidentPublished()
                  : routed;
            });
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      ForgotPasswordDetailsOtpCommand command, AtomicBoolean incidentPublished) {
    return operation
        .execute(command)
        .flatMap(
            execution -> {
              if (execution.failureOrigin() != null) {
                failureEventPort.publish(failureEvent(command, execution));
                incidentPublished.set(true);
                if (execution.result().status()
                    == ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE) {
                  return Mono.just(
                      responses.toTransientResponse(
                          execution.result().withIncidentPublished()));
                }
                return Mono.error(new ForgotPasswordDetailsOtpIncidentPublishedException());
              }
              if (execution.result().status()
                  == ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR) {
                return Mono.error(new ForgotPasswordDetailsOtpExpectedFailureException());
              }
              HydratedIdempotentResponse response =
                  responses.toTransientResponse(execution.result());
              return Mono.just(
                  execution.replayReference() == null
                      ? response
                      : response.withProtectedReplayReference(execution.replayReference()));
            });
  }

  private static FailureEvent failureEvent(
      ForgotPasswordDetailsOtpCommand command,
      ForgotPasswordDetailsOtpProtectedExecutionResult execution) {
    boolean unavailable =
        execution.result().status() == ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE;
    return new FailureEvent(
        "forgot-password.details-otp.failed",
        RequestIdLogSanitizer.sanitize(command.requestId()),
        "FORGOT_PASSWORD",
        "DETAILS_AND_OTP",
        execution.failureOrigin(),
        "DETAILS_OTP_TECHNICAL_FAILURE",
        unavailable ? "err.system.unavailable" : "err.system.unexpected",
        unavailable ? 503 : 500,
        false,
        Map.of("stage", execution.failureStage()));
  }

  private ForgotPasswordDetailsOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(ForgotPasswordDetailsOtpResult.Status.IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          simple(ForgotPasswordDetailsOtpResult.Status.KEY_REUSED);
      case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING -> ForgotPasswordDetailsOtpResult.system();
    };
  }

  private ForgotPasswordDetailsOtpResult simple(ForgotPasswordDetailsOtpResult.Status status) {
    return new ForgotPasswordDetailsOtpResult(status, null, 0, 0, List.of(), false);
  }
}
