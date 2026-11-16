package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend.ForgotPasswordOtpResendExecutionBridge;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/** Thin OTP_RESEND integration over the repository-wide shared idempotency executor. */
public final class IdempotentResendForgotPasswordOtpService
    implements ResendForgotPasswordOtpUseCase {
  private final IdempotencyExecutor executor;
  private final ForgotPasswordOtpResendProtectedOperation operation;
  private final ForgotPasswordOtpResendExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;
  private final Duration ttl;
  private final FailureEventPort failureEvents;

  public IdempotentResendForgotPasswordOtpService(
      IdempotencyExecutor executor,
      ForgotPasswordOtpResendProtectedOperation operation,
      ForgotPasswordOtpResendExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy,
      Duration ttl,
      FailureEventPort failureEvents) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.ttl = Objects.requireNonNull(ttl);
    this.failureEvents = Objects.requireNonNull(failureEvents);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("OTP_RESEND idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<ResendForgotPasswordOtpResult> resend(ResendForgotPasswordOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
        () -> {
          PublicationTracker publication = new PublicationTracker();
          AtomicReference<RuntimeException> reportedOperationFailure = new AtomicReference<>();
          var execution =
              new IdempotentExecutionCommand(
                  policy.operationId(
                      ForgotPasswordIdempotencyAction.OTP_RESEND,
                      command.sessionId().value(),
                      command.idempotencyKey()),
                  policy.otpResend(),
                  ttl,
                  () -> executeAcquired(command, publication, reportedOperationFailure),
                  IdempotencyFailureClassifiers.possibleSideEffect(),
                  response -> responses.capture(response, response.protectedReplayReference()),
                  responses::hydrate);
          return Mono.defer(() -> executor.execute(execution))
              .map(
                  result -> {
                    ResendForgotPasswordOtpResult routed =
                        route(result.lifecycleOutcome(), result.terminalResponse());
                    return result.lifecycleOutcome() == IdempotencyReservationOutcome.ACQUIRED
                        ? withReceipt(routed, publication)
                        : routed;
                  })
              .onErrorResume(
                  cause -> {
                    if (cause != reportedOperationFailure.get()) {
                      publish(command, ForgotPasswordOtpResendIncident.IDEMPOTENCY, 503);
                      publication.record(true);
                    }
                    return Mono.just(
                        withReceipt(
                            simple(ResendForgotPasswordOtpStatus.SYSTEM_FAILED), publication));
                  });
        });
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      ResendForgotPasswordOtpCommand command,
      PublicationTracker publication,
      AtomicReference<RuntimeException> reportedOperationFailure) {
    return Mono.defer(() -> operation.execute(command))
        .onErrorResume(
            cause -> {
              publish(command, ForgotPasswordOtpResendIncident.PROTECTED_OPERATION, 503);
              publication.record(true);
              RuntimeException signal = new ReportedOperationFailureException();
              reportedOperationFailure.set(signal);
              return Mono.error(signal);
            })
        .flatMap(
            execution -> {
              for (ForgotPasswordOtpResendIncident incident : execution.incidents()) {
                publish(command, incident, publicHttpStatus(execution.result().status()));
                publication.record(incident.terminal());
              }
              if (execution.result().status() == ResendForgotPasswordOtpStatus.SYSTEM_FAILED) {
                RuntimeException signal = new ReportedOperationFailureException();
                reportedOperationFailure.set(signal);
                return Mono.error(signal);
              }
              HydratedIdempotentResponse response =
                  responses.toTransientResponse(execution.result());
              return Mono.just(
                  execution.replayReference() == null
                      ? response
                      : response.withProtectedReplayReference(execution.replayReference()));
            });
  }

  private void publish(
      ResendForgotPasswordOtpCommand command,
      ForgotPasswordOtpResendIncident incident,
      int httpStatus) {
    failureEvents.publish(
        new FailureEvent(
            "forgot-password.resend.failed",
            RequestIdLogSanitizer.sanitize(command.requestId()),
            "FORGOT_PASSWORD",
            "OTP_RESEND",
            FailureOrigin.LOCAL_SYSTEM,
            incident.internalCode(),
            null,
            httpStatus,
            false,
            Map.of("stage", incident.stage())));
  }

  private static int publicHttpStatus(ResendForgotPasswordOtpStatus status) {
    return switch (status) {
      case RESENT, COMPLETED_REPLAY -> 200;
      case COOLDOWN_ACTIVE, RESEND_LIMIT_REACHED -> 429;
      case EXPIRED, PREVIOUS_FAILED -> 422;
      case AUTH_FLOW_INVALID -> 400;
      case SECURITY_REJECTED -> 403;
      case IDEMPOTENCY_KEY_REUSED -> 409;
      case DELIVERY_FAILED, UNKNOWN_OUTCOME, SYSTEM_FAILED -> 503;
    };
  }

  private ResendForgotPasswordOtpResult withReceipt(
      ResendForgotPasswordOtpResult result, PublicationTracker publication) {
    boolean terminal = publication.terminal();
    return publication.published()
        ? result.withPublicationReceipt(new ForgotPasswordOtpResendPublicationReceipt(terminal))
        : result;
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

  private ResendForgotPasswordOtpResult simple(ResendForgotPasswordOtpStatus status) {
    return new ResendForgotPasswordOtpResult(status, null, null, null, null, null, null);
  }

  private static final class PublicationTracker {
    private final AtomicBoolean published = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();

    void record(boolean terminalFailure) {
      published.set(true);
      if (terminalFailure) {
        terminal.set(true);
      }
    }

    boolean published() {
      return published.get();
    }

    boolean terminal() {
      return terminal.get();
    }
  }

  private static final class ReportedOperationFailureException extends RuntimeException {
    private ReportedOperationFailureException() {
      super(null, null, false, false);
    }
  }
}
