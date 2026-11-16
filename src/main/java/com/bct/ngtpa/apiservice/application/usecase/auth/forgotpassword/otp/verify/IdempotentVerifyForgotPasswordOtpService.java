package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify.ForgotPasswordOtpVerifyExecutionBridge;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/** Thin OTP_VERIFY integration over the repository-wide shared idempotency executor. */
public final class IdempotentVerifyForgotPasswordOtpService
    implements VerifyForgotPasswordOtpUseCase {
  private static final String FAILURE_EVENT = "forgot-password.otp-verify.failed";

  private final IdempotencyExecutor executor;
  private final ForgotPasswordOtpVerifyProtectedOperation operation;
  private final ForgotPasswordOtpVerifyExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;
  private final FailureEventPort failureEvents;

  public IdempotentVerifyForgotPasswordOtpService(
      IdempotencyExecutor executor,
      ForgotPasswordOtpVerifyProtectedOperation operation,
      ForgotPasswordOtpVerifyExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy,
      FailureEventPort failureEvents) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.failureEvents = Objects.requireNonNull(failureEvents);
  }

  @Override
  public Mono<VerifyForgotPasswordOtpResult> verify(VerifyForgotPasswordOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
        () -> {
          PublicationTracker publication = new PublicationTracker();
          AtomicBoolean publicationFailed = new AtomicBoolean();
          AtomicReference<RuntimeException> reportedOperationFailure = new AtomicReference<>();
          return Mono.defer(
                  () -> {
                    var execution =
                        new IdempotentExecutionCommand(
                            policy.operationId(
                                ForgotPasswordIdempotencyAction.OTP_VERIFY,
                                command.sessionId().value(),
                                command.idempotencyKey()),
                            policy.otpVerify(),
                            command.idempotencyTtl(),
                            () ->
                                executeAcquired(
                                    command,
                                    publication,
                                    publicationFailed,
                                    reportedOperationFailure),
                            IdempotencyFailureClassifiers.possibleSideEffect(),
                            responses::capture,
                            responses::hydrate);
                    return Mono.defer(() -> executor.execute(execution))
                        .map(
                            result -> {
                              VerifyForgotPasswordOtpResult routed =
                                  route(result.lifecycleOutcome(), result.terminalResponse());
                              return result.lifecycleOutcome()
                                      == IdempotencyReservationOutcome.ACQUIRED
                                  ? withReceipt(routed, publication)
                                  : routed;
                            });
                  })
              .onErrorResume(
                  cause -> {
                    if (publicationFailed.get()) {
                      return Mono.error(cause);
                    }
                    if (cause == reportedOperationFailure.get()) {
                      return Mono.just(
                          withReceipt(
                              simple(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED), publication));
                    }
                    return Mono.defer(
                        () -> {
                          publishAndRecord(
                              command,
                              ForgotPasswordOtpVerifyIncident.IDEMPOTENCY,
                              publication,
                              publicationFailed);
                          return Mono.just(
                              withReceipt(
                                  simple(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED),
                                  publication));
                        });
                  });
        });
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      VerifyForgotPasswordOtpCommand command,
      PublicationTracker publication,
      AtomicBoolean publicationFailed,
      AtomicReference<RuntimeException> reportedOperationFailure) {
    return Mono.defer(() -> operation.execute(command))
        .onErrorResume(
            failure -> {
              publishAndRecord(
                  command,
                  ForgotPasswordOtpVerifyIncident.PROTECTED_OPERATION,
                  publication,
                  publicationFailed);
              RuntimeException signal = new ReportedOperationFailureException();
              reportedOperationFailure.set(signal);
              return Mono.error(signal);
            })
        .flatMap(
            execution -> {
              if (execution.incident() != null) {
                RuntimeException signal = new ReportedOperationFailureException();
                reportedOperationFailure.set(signal);
                if (!execution.incident().terminalPublicationRequired()) {
                  return Mono.error(signal);
                }
                publishAndRecord(command, execution.incident(), publication, publicationFailed);
                return Mono.error(signal);
              }
              return Mono.just(responses.toTransientResponse(execution.result()));
            });
  }

  private void publishAndRecord(
      VerifyForgotPasswordOtpCommand command,
      ForgotPasswordOtpVerifyIncident incident,
      PublicationTracker publication,
      AtomicBoolean publicationFailed) {
    try {
      failureEvents.publish(
          new FailureEvent(
              FAILURE_EVENT,
              RequestIdLogSanitizer.sanitize(command.requestId()),
              "FORGOT_PASSWORD",
              "OTP_VERIFY",
              FailureOrigin.LOCAL_SYSTEM,
              incident.internalCode(),
              null,
              503,
              false,
              Map.of("stage", incident.stage())));
    } catch (RuntimeException failure) {
      publicationFailed.set(true);
      throw failure;
    }
    publication.markPublished();
  }

  private VerifyForgotPasswordOtpResult withReceipt(
      VerifyForgotPasswordOtpResult result, PublicationTracker publication) {
    return publication.published()
        ? result.withPublicationReceipt(new ForgotPasswordOtpVerifyPublicationReceipt(true))
        : result;
  }

  private VerifyForgotPasswordOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          simple(VerifyForgotPasswordOtpStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.WAIT);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          simple(VerifyForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED, WorkflowNextStep.NONE);
      case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING ->
          simple(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE);
    };
  }

  private VerifyForgotPasswordOtpResult simple(VerifyForgotPasswordOtpStatus status) {
    return simple(status, WorkflowNextStep.NONE);
  }

  private VerifyForgotPasswordOtpResult simple(
      VerifyForgotPasswordOtpStatus status, WorkflowNextStep nextStep) {
    return new VerifyForgotPasswordOtpResult(status, nextStep, null, null);
  }

  private static final class PublicationTracker {
    private final AtomicBoolean published = new AtomicBoolean();

    void markPublished() {
      published.set(true);
    }

    boolean published() {
      return published.get();
    }
  }

  private static final class ReportedOperationFailureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private ReportedOperationFailureException() {
      super(null, null, false, false);
    }
  }
}
