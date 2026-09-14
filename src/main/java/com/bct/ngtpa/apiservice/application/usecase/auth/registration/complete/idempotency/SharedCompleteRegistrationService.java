package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionPreDispatchException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationUsernameCheckContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

/** Parallel Registration Complete coordinator backed only by the shared idempotency executor. */
public final class SharedCompleteRegistrationService implements CompleteRegistrationUseCase {
  private static final String SOURCE_SERVER = "SERVER";
  private final RegistrationProtectedAuthFlowContextLoader contexts;
  private final AuthFlowSessionExpiryRefreshPort expiry;
  private final RegistrationUsernameCheckContextLoader usernames;
  private final RegistrationPasswordValidator passwords;
  private final IdempotencyExecutor executor;
  private final RegistrationCompleteProtectedOperation operation;
  private final RegistrationCompleteSharedExecutionBridge responses;
  private final RegistrationSharedIdempotencyIdentityFactory identities;
  private final RegistrationSharedFingerprintFactory fingerprints;
  private final Clock clock;

  public SharedCompleteRegistrationService(
      RegistrationProtectedAuthFlowContextLoader contexts,
      AuthFlowSessionExpiryRefreshPort expiry,
      RegistrationUsernameCheckContextLoader usernames,
      RegistrationPasswordValidator passwords,
      IdempotencyExecutor executor,
      RegistrationCompleteProtectedOperation operation,
      RegistrationCompleteSharedExecutionBridge responses,
      RegistrationSharedIdempotencyIdentityFactory identities,
      RegistrationSharedFingerprintFactory fingerprints,
      Clock clock) {
    this.contexts = Objects.requireNonNull(contexts);
    this.expiry = Objects.requireNonNull(expiry);
    this.usernames = Objects.requireNonNull(usernames);
    this.passwords = Objects.requireNonNull(passwords);
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.identities = Objects.requireNonNull(identities);
    this.fingerprints = Objects.requireNonNull(fingerprints);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<CompleteRegistrationResult> complete(CompleteRegistrationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return contexts
        .load(
            new RegistrationProtectedAuthFlowContextCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                command.origin(),
                command.referer(),
                command.correlationId(),
                Set.of(AuthFlowStatus.USERNAME_VERIFIED)))
        .flatMap(context -> authorize(command, context));
  }

  private Mono<CompleteRegistrationResult> authorize(
      CompleteRegistrationCommand command, RegistrationProtectedAuthFlowContextResult context) {
    if (!context.isValid()) {
      return Mono.just(
          failure(
              context.status() == RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID
                  ? CompleteRegistrationResultStatus.CSRF_INVALID
                  : CompleteRegistrationResultStatus.INVALID_AUTH_FLOW,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.AUTH_FLOW_INVALID.value()));
    }
    return expiry
        .refresh(command.authFlowSessionId(), clock.instant())
        .flatMap(session -> prepare(command, context, session));
  }

  private Mono<CompleteRegistrationResult> prepare(
      CompleteRegistrationCommand command,
      RegistrationProtectedAuthFlowContextResult context,
      AuthFlowSession session) {
    if (session.status() != AuthFlowStatus.USERNAME_VERIFIED) {
      return Mono.just(
          failure(
              CompleteRegistrationResultStatus.INVALID_STATE,
              WorkflowNextStep.PASSWORD_SETUP,
              RegistrationErrorKey.STATE_INVALID.value()));
    }
    UsernameCheckState username;
    try {
      username = usernames.load(session);
    } catch (RuntimeException exception) {
      return Mono.just(
          failure(
              CompleteRegistrationResultStatus.INVALID_AUTH_FLOW,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.AUTH_FLOW_INVALID.value()));
    }
    var validation =
        passwords.validate(command.password(), command.confirmPassword(), username.username());
    if (!validation.valid()) {
      return Mono.just(
          new CompleteRegistrationResult(
              CompleteRegistrationResultStatus.VALIDATION_FAILED,
              WorkflowNextStep.PASSWORD_SETUP,
              false,
              false,
              List.of(),
              validation.errors()));
    }
    return execute(
        command,
        new RegistrationCompleteProtectedOperationInput(
            command, context.registrationContext(), username, session));
  }

  private Mono<CompleteRegistrationResult> execute(
      CompleteRegistrationCommand command, RegistrationCompleteProtectedOperationInput input) {
    String sessionScope = command.authFlowSessionId().value();
    var execution =
        new IdempotentExecutionCommand(
            identities.create(
                RegistrationSharedIdempotencyAction.COMPLETE,
                command.idempotencyKey().value(),
                sessionScope),
            fingerprints.create(
                RegistrationSharedIdempotencyAction.COMPLETE,
                List.of(sessionScope, sha256(command.password()))),
            command.idempotencyTtl(),
            () -> operation.execute(input).map(responses::toTransientResponse),
            failure ->
                failure instanceof RegistrationCompletionPreDispatchException
                    ? IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT
                    : IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT,
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private CompleteRegistrationResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          result(CompleteRegistrationResultStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.WAIT);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          failure(
              CompleteRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
              WorkflowNextStep.NONE,
              SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value());
      case FAILED_EXISTING ->
          failure(
              CompleteRegistrationResultStatus.SYSTEM_FAILED,
              WorkflowNextStep.PASSWORD_SETUP,
              SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
      case UNKNOWN_OUTCOME_EXISTING ->
          failure(
              CompleteRegistrationResultStatus.UNKNOWN_OUTCOME,
              WorkflowNextStep.RETURN_TO_LOGIN,
              RegistrationErrorKey.REGISTRATION_COMPLETE_OUTCOMEUNKNOWN.value());
    };
  }

  private CompleteRegistrationResult result(
      CompleteRegistrationResultStatus status, WorkflowNextStep nextStep) {
    return new CompleteRegistrationResult(status, nextStep, false, false, List.of(), List.of());
  }

  private CompleteRegistrationResult failure(
      CompleteRegistrationResultStatus status, WorkflowNextStep nextStep, String code) {
    return new CompleteRegistrationResult(
        status,
        nextStep,
        false,
        false,
        List.of(),
        List.of(
            new WorkflowErrorDescriptor(
                WorkflowErrorType.BUSINESS,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER)));
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Password hash unavailable");
    }
  }
}
