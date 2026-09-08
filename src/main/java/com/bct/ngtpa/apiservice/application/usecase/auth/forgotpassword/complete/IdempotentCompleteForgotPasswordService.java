package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete.ForgotPasswordCompleteExecutionBridge;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Thin COMPLETE integration over the repository-wide shared idempotency executor. */
public final class IdempotentCompleteForgotPasswordService
    implements CompleteForgotPasswordUseCase {
  private static final String SOURCE_SERVER = "SERVER";
  private final IdempotencyExecutor executor;
  private final AuthFlowSessionPort sessions;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final ForgotPasswordCompleteProtectedOperation operation;
  private final ForgotPasswordCompleteExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;

  public IdempotentCompleteForgotPasswordService(
      IdempotencyExecutor executor,
      AuthFlowSessionPort sessions,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      ForgotPasswordCompleteProtectedOperation operation,
      ForgotPasswordCompleteExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy) {
    this.executor = Objects.requireNonNull(executor);
    this.sessions = Objects.requireNonNull(sessions);
    this.protector = Objects.requireNonNull(protector);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
  }

  @Override
  public Mono<CompleteForgotPasswordResult> complete(CompleteForgotPasswordCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                ForgotPasswordIdempotencyAction.COMPLETE,
                command.authFlowSessionId().value(),
                command.idempotencyKey().value()),
            policy.complete(),
            command.idempotencyTtl(),
            () -> executeAcquired(command),
            IdempotencyFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()))
        .onErrorReturn(systemFailed(WorkflowNextStep.NONE));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(CompleteForgotPasswordCommand command) {
    return sessions
        .loadSnapshot(command.authFlowSessionId())
        .flatMap(
            optional ->
                optional
                    .map(snapshot -> executeLoaded(command, snapshot))
                    .orElseGet(() -> Mono.just(responses.toTransientResponse(invalidAuthFlow()))));
  }

  private Mono<HydratedIdempotentResponse> executeLoaded(
      CompleteForgotPasswordCommand command,
      com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot snapshot) {
    ForgotPasswordAuthFlowContext context = loadContext(snapshot.session().data().attributes());
    if (context == null) {
      return Mono.just(responses.toTransientResponse(invalidAuthFlow()));
    }
    return operation
        .execute(new ForgotPasswordCompleteProtectedOperationInput(command, context, snapshot))
        .flatMap(
            execution -> {
              if (execution.outcome()
                  == ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL) {
                return Mono.just(responses.toTransientResponse(execution.result()));
              }
              if (execution.outcome()
                  == ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE) {
                return Mono.error(
                    new IllegalStateException(
                        "Forgot Password COMPLETE protected operation failed"));
              }
              if (execution.outcome()
                  == ForgotPasswordCompleteProtectedExecutionResult.Outcome.UNKNOWN_OUTCOME) {
                return Mono.error(
                    new IllegalStateException(
                        "Forgot Password COMPLETE protected operation outcome is unknown"));
              }
              return Mono.error(
                  new IllegalStateException("Unsupported COMPLETE execution outcome"));
            });
  }

  private ForgotPasswordAuthFlowContext loadContext(Map<String, String> attributes) {
    if (attributes == null) return null;
    String ciphertext = attributes.get(CONTEXT_CIPHERTEXT);
    String keyVersion = attributes.get(CONTEXT_KEY_VERSION);
    String algorithm = attributes.get(CONTEXT_ALGORITHM);
    if (ciphertext == null || ciphertext.isBlank() || keyVersion == null || keyVersion.isBlank()) {
      return null;
    }
    try {
      return protector.unprotect(
          new ForgotPasswordAuthFlowEncryptedContext(
              ciphertext,
              keyVersion,
              algorithm == null || algorithm.isBlank()
                  ? ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM
                  : algorithm));
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private CompleteForgotPasswordResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          simple(
              CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS,
              WorkflowNextStep.WAIT,
              "err.idempotency.in.progress");
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          simple(
              CompleteForgotPasswordStatus.IDEMPOTENCY_KEY_REUSED,
              WorkflowNextStep.NONE,
              "err.idempotency.key.reused");
      case FAILED_EXISTING -> systemFailed(WorkflowNextStep.PASSWORD_SETUP);
      case UNKNOWN_OUTCOME_EXISTING -> unknownOutcome();
    };
  }

  private CompleteForgotPasswordResult invalidAuthFlow() {
    return simple(
        CompleteForgotPasswordStatus.INVALID_AUTH_FLOW,
        WorkflowNextStep.NONE,
        ForgotPasswordCompleteErrorKeys.AUTH_FLOW_INVALID);
  }

  private CompleteForgotPasswordResult unknownOutcome() {
    return simple(
        CompleteForgotPasswordStatus.UNKNOWN_OUTCOME,
        WorkflowNextStep.NONE,
        ForgotPasswordCompleteErrorKeys.SYSTEM_UNAVAILABLE);
  }

  private CompleteForgotPasswordResult systemFailed(WorkflowNextStep nextStep) {
    return simple(
        CompleteForgotPasswordStatus.SYSTEM_FAILED,
        nextStep,
        ForgotPasswordCompleteErrorKeys.SYSTEM_UNAVAILABLE);
  }

  private CompleteForgotPasswordResult simple(
      CompleteForgotPasswordStatus status, WorkflowNextStep nextStep, String code) {
    return new CompleteForgotPasswordResult(
        status,
        nextStep,
        false,
        false,
        List.of(
            new WorkflowErrorDescriptor(
                status == CompleteForgotPasswordStatus.SYSTEM_FAILED
                        || status == CompleteForgotPasswordStatus.UNKNOWN_OUTCOME
                    ? WorkflowErrorType.SYSTEM
                    : WorkflowErrorType.BUSINESS,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER)));
  }
}
