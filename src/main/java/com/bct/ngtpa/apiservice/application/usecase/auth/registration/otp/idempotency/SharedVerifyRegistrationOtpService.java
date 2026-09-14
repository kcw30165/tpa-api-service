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
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFailureClassifiers;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerifyErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** OTP Verify coordinator backed only by the shared idempotency executor. */
public final class SharedVerifyRegistrationOtpService implements VerifyRegistrationOtpUseCase {
  private static final String SOURCE_SERVER = "SERVER";
  private final RegistrationProtectedAuthFlowContextLoader contexts;
  private final IdempotencyExecutor executor;
  private final RegistrationOtpVerifyProtectedOperation operation;
  private final RegistrationOtpVerifySharedExecutionBridge responses;
  private final RegistrationSharedIdempotencyIdentityFactory identities;
  private final RegistrationSharedFingerprintFactory fingerprints;

  public SharedVerifyRegistrationOtpService(
      RegistrationProtectedAuthFlowContextLoader contexts,
      IdempotencyExecutor executor,
      RegistrationOtpVerifyProtectedOperation operation,
      RegistrationOtpVerifySharedExecutionBridge responses,
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
  public Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!validOtp(command.otpCode())) {
      return Mono.just(
          failure(
              VerifyRegistrationOtpResultStatus.VALIDATION_FAILED,
              WorkflowNextStep.VERIFY_OTP,
              RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
              null,
              RegistrationIdempotencyStartOutcome.STARTED));
    }
    return contexts
        .load(
            new RegistrationProtectedAuthFlowContextCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                command.origin(),
                command.referer(),
                command.correlationId()))
        .flatMap(context -> authorizeThenExecute(command, context));
  }

  private Mono<VerifyRegistrationOtpResult> authorizeThenExecute(
      VerifyRegistrationOtpCommand command, RegistrationProtectedAuthFlowContextResult context) {
    if (context.status() == RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID) {
      return Mono.just(
          failure(
              VerifyRegistrationOtpResultStatus.CSRF_INVALID,
              WorkflowNextStep.NONE,
              RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID,
              null,
              RegistrationIdempotencyStartOutcome.STARTED));
    }
    if (!context.isValid()) {
      return Mono.just(
          failure(
              VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
              WorkflowNextStep.NONE,
              RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID,
              null,
              RegistrationIdempotencyStartOutcome.STARTED));
    }
    String sessionScope = command.authFlowSessionId().value();
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                RegistrationSharedIdempotencyAction.OTP_VERIFY,
                command.idempotencyKey().value(),
                sessionScope),
            fingerprints.create(
                RegistrationSharedIdempotencyAction.OTP_VERIFY,
                List.of(sessionScope, sha256(command.otpCode()))),
            command.idempotencyTtl(),
            () -> operation.execute(command, context).map(responses::toTransientResponse),
            RegistrationSharedFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private VerifyRegistrationOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          failure(
              VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
              WorkflowNextStep.WAIT,
              SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
              null,
              RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          failure(
              VerifyRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED,
              WorkflowNextStep.NONE,
              RegistrationOtpVerifyErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED,
              null,
              RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
      case FAILED_EXISTING -> systemFailure(RegistrationIdempotencyStartOutcome.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING ->
          systemFailure(RegistrationIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING);
    };
  }

  private VerifyRegistrationOtpResult systemFailure(RegistrationIdempotencyStartOutcome outcome) {
    return failure(
        VerifyRegistrationOtpResultStatus.SYSTEM_FAILED,
        WorkflowNextStep.NONE,
        RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID,
        null,
        outcome);
  }

  private VerifyRegistrationOtpResult failure(
      VerifyRegistrationOtpResultStatus status,
      WorkflowNextStep nextStep,
      String code,
      Integer remaining,
      RegistrationIdempotencyStartOutcome outcome) {
    return new VerifyRegistrationOtpResult(
        status,
        nextStep,
        List.of(),
        remaining,
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

  private boolean validOtp(String otp) {
    return otp != null && otp.matches("\\d{6}");
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("OTP hash unavailable");
    }
  }
}
