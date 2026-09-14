package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Production Registration Start coordinator backed only by the shared idempotency executor. */
public final class SharedStartRegistrationService implements StartRegistrationUseCase {
  private static final String ANONYMOUS_START_SCOPE = "registration-start-anonymous-v1";
  private final IdempotencyExecutor executor;
  private final RegistrationStartProtectedOperation operation;
  private final RegistrationStartSharedExecutionBridge responses;
  private final RegistrationSharedIdempotencyIdentityFactory identities;
  private final RegistrationSharedFingerprintFactory fingerprints;

  public SharedStartRegistrationService(
      IdempotencyExecutor executor,
      RegistrationStartProtectedOperation operation,
      RegistrationStartSharedExecutionBridge responses,
      RegistrationSharedIdempotencyIdentityFactory identities,
      RegistrationSharedFingerprintFactory fingerprints) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.identities = Objects.requireNonNull(identities);
    this.fingerprints = Objects.requireNonNull(fingerprints);
  }

  @Override
  public Mono<StartRegistrationResult> start(StartRegistrationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                RegistrationSharedIdempotencyAction.START,
                command.idempotencyKey().value(),
                ANONYMOUS_START_SCOPE),
            fingerprints.create(
                RegistrationSharedIdempotencyAction.START, fingerprintParts(command)),
            command.idempotencyTtl(),
            () -> operation.execute(command).map(responses::toTransientResponse),
            RegistrationSharedFailureClassifiers.possibleSideEffect(),
            response -> responses.capture(response, command.idempotencyTtl()),
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private StartRegistrationResult route(
      IdempotencyReservationOutcome outcome,
      com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          failure(
              StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS,
              WorkflowNextStep.WAIT,
              SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS,
              "Idempotency-Key",
              RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          failure(
              StartRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
              WorkflowNextStep.NONE,
              SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED,
              "Idempotency-Key",
              RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
      case FAILED_EXISTING -> systemFailure(RegistrationIdempotencyStartOutcome.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING ->
          systemFailure(RegistrationIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING);
    };
  }

  private StartRegistrationResult systemFailure(RegistrationIdempotencyStartOutcome outcome) {
    return failure(
        StartRegistrationResultStatus.SYSTEM_FAILED,
        WorkflowNextStep.NONE,
        SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE,
        "PAGE",
        outcome);
  }

  private StartRegistrationResult failure(
      StartRegistrationResultStatus status,
      WorkflowNextStep nextStep,
      com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey key,
      String target,
      RegistrationIdempotencyStartOutcome outcome) {
    return new StartRegistrationResult(
        status,
        nextStep,
        null,
        0,
        0,
        0,
        null,
        null,
        List.of(),
        List.of(new StartRegistrationError(key, target)),
        outcome,
        null);
  }

  private List<String> fingerprintParts(StartRegistrationCommand command) {
    String email = command.email() == null ? "" : command.email().trim().toLowerCase(Locale.ROOT);
    String mobile = command.mobile() == null ? "" : command.mobile().trim();
    String type = !email.isBlank() ? "email" : (!mobile.isBlank() ? "mobile" : "none");
    String value = !email.isBlank() ? email : mobile;
    return List.of(
        normalize(command.identityType()),
        normalize(command.identityValue()),
        normalize(command.dateOfBirth()),
        type,
        value,
        normalize(command.captchaChallengeId()));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
