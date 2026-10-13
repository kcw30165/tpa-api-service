package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFailureClassifiers;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** OTP Resend coordinator backed only by the shared idempotency executor. */
public final class SharedResendRegistrationOtpService implements ResendRegistrationOtpUseCase {
  private static final String SOURCE_SERVER = "SERVER";
  private final RegistrationProtectedAuthFlowContextLoader contexts;
  private final IdempotencyExecutor executor;
  private final RegistrationOtpResendProtectedOperation operation;
  private final RegistrationOtpResendSharedExecutionBridge responses;
  private final RegistrationSharedIdempotencyIdentityFactory identities;
  private final RegistrationSharedFingerprintFactory fingerprints;

  public SharedResendRegistrationOtpService(
      RegistrationProtectedAuthFlowContextLoader contexts,
      IdempotencyExecutor executor,
      RegistrationOtpResendProtectedOperation operation,
      RegistrationOtpResendSharedExecutionBridge responses,
      RegistrationSharedIdempotencyIdentityFactory identities,
      RegistrationSharedFingerprintFactory fingerprints) {
    this.contexts = Objects.requireNonNull(contexts);
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.identities = Objects.requireNonNull(identities);
    this.fingerprints = Objects.requireNonNull(fingerprints);
  }

  @Override
  public Mono<ResendRegistrationOtpResult> resend(ResendRegistrationOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return contexts
        .load(
            RegistrationProtectedAuthFlowContextCommand.forOtpSent(
                command.authFlowSessionId(),
                command.csrfToken(),
                command.origin(),
                command.referer(),
                command.correlationId()))
        .flatMap(context -> authorizeThenExecute(command, context));
  }

  private Mono<ResendRegistrationOtpResult> authorizeThenExecute(
      ResendRegistrationOtpCommand command, RegistrationProtectedAuthFlowContextResult context) {
    if (context.status() == RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID) {
      return Mono.just(
          failure(
              ResendRegistrationOtpResultStatus.CSRF_INVALID,
              WorkflowNextStep.NONE,
              RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID,
              RegistrationIdempotencyStartOutcome.STARTED));
    }
    if (!context.isValid()) {
      return Mono.just(
          failure(
              ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
              WorkflowNextStep.NONE,
              RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID,
              RegistrationIdempotencyStartOutcome.STARTED));
    }
    String sessionScope = command.authFlowSessionId().value();
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                RegistrationSharedIdempotencyAction.OTP_RESEND,
                command.idempotencyKey().value(),
                sessionScope),
            fingerprints.create(
                RegistrationSharedIdempotencyAction.OTP_RESEND, List.of(sessionScope)),
            command.idempotencyTtl(),
            () -> operation.execute(command, context).map(responses::toTransientResponse),
            RegistrationSharedFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private ResendRegistrationOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          failure(
              ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
              WorkflowNextStep.WAIT,
              SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
              RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          failure(
              ResendRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED,
              WorkflowNextStep.NONE,
              RegistrationOtpResendErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED,
              RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
      case FAILED_EXISTING -> systemFailure(RegistrationIdempotencyStartOutcome.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING ->
          systemFailure(RegistrationIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING);
    };
  }

  private ResendRegistrationOtpResult systemFailure(RegistrationIdempotencyStartOutcome outcome) {
    return failure(
        ResendRegistrationOtpResultStatus.SYSTEM_FAILED,
        WorkflowNextStep.NONE,
        RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED,
        outcome);
  }

  private ResendRegistrationOtpResult failure(
      ResendRegistrationOtpResultStatus status,
      WorkflowNextStep nextStep,
      String code,
      RegistrationIdempotencyStartOutcome outcome) {
    return new ResendRegistrationOtpResult(
        status,
        nextStep,
        null,
        0,
        0,
        0,
        null,
        List.of(),
        List.of(
            new WorkflowErrorDescriptor(
                WorkflowErrorType.FORM,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER)),
        outcome,
        null);
  }
}
