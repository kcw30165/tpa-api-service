package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete.ForgotPasswordCompleteExecutionBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/** Thin COMPLETE integration over the repository-wide shared idempotency executor. */
public final class IdempotentCompleteForgotPasswordService
    implements CompleteForgotPasswordUseCase {
  private static final String FAILURE_EVENT = "forgot-password.complete.failed";
  private static final String SOURCE_SERVER = "SERVER";
  private final IdempotencyExecutor executor;
  private final AuthFlowSessionPort sessions;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final ForgotPasswordCompleteProtectedOperation operation;
  private final ForgotPasswordCompleteExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;
  private final FailureEventPort failureEvents;

  public IdempotentCompleteForgotPasswordService(
      IdempotencyExecutor executor,
      AuthFlowSessionPort sessions,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      ForgotPasswordCompleteProtectedOperation operation,
      ForgotPasswordCompleteExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy,
      FailureEventPort failureEvents) {
    this.executor = Objects.requireNonNull(executor);
    this.sessions = Objects.requireNonNull(sessions);
    this.protector = Objects.requireNonNull(protector);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.failureEvents = Objects.requireNonNull(failureEvents);
  }

  @Override
  public Mono<CompleteForgotPasswordResult> complete(CompleteForgotPasswordCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
        () -> {
          PublicationTracker publication = new PublicationTracker();
          AtomicBoolean publicationFailed = new AtomicBoolean();
          AtomicReference<RuntimeException> reportedOperationFailure = new AtomicReference<>();
          AtomicReference<RuntimeException> noIncidentOutcome = new AtomicReference<>();
          var execution =
              new IdempotentExecutionCommand(
                  policy.operationId(
                      ForgotPasswordIdempotencyAction.COMPLETE,
                      command.authFlowSessionId().value(),
                      command.idempotencyKey().value()),
                  policy.complete(),
                  command.idempotencyTtl(),
                  () ->
                      executeAcquired(
                          command, publication, publicationFailed, reportedOperationFailure,
                          noIncidentOutcome),
                  IdempotencyFailureClassifiers.possibleSideEffect(),
                  responses::capture,
                  responses::hydrate);
          return Mono.defer(() -> executor.execute(execution))
              .map(
                  result ->
                      withReceipt(
                          route(result.lifecycleOutcome(), result.terminalResponse()),
                          publication))
              .onErrorResume(
                  failure -> {
                    if (publicationFailed.get()) {
                      return Mono.just(systemFailed(WorkflowNextStep.NONE));
                    }
                    if (failure == reportedOperationFailure.get()) {
                      return Mono.just(withReceipt(systemFailed(WorkflowNextStep.NONE), publication));
                    }
                    if (failure == noIncidentOutcome.get()) {
                      return Mono.just(systemFailed(WorkflowNextStep.NONE));
                    }
                    return Mono.defer(
                            () -> {
                              publishAndRecord(
                                  command,
                                  ForgotPasswordCompleteIncident.local(
                                      "COMPLETE_IDEMPOTENCY_FAILURE", "idempotency"),
                                  publication,
                                  publicationFailed);
                              return Mono.just(
                                  withReceipt(systemFailed(WorkflowNextStep.NONE), publication));
                            })
                        .onErrorResume(
                            publishFailure -> Mono.just(systemFailed(WorkflowNextStep.NONE)));
                  });
        });
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      CompleteForgotPasswordCommand command,
      PublicationTracker publication,
      AtomicBoolean publicationFailed,
      AtomicReference<RuntimeException> reportedOperationFailure,
      AtomicReference<RuntimeException> noIncidentOutcome) {
    return Mono.defer(() -> sessions.loadSnapshot(command.authFlowSessionId()))
        .onErrorMap(
            failure ->
                new ForgotPasswordCompletionTechnicalFailure(
                    FailureOrigin.LOCAL_SYSTEM, "session-read"))
        .flatMap(
            optional ->
                optional
                    .map(
                        snapshot ->
                            executeLoaded(
                                command,
                                snapshot,
                                publication,
                                publicationFailed,
                                reportedOperationFailure,
                                noIncidentOutcome))
                    .orElseGet(
                        () -> Mono.just(responses.toTransientResponse(invalidAuthFlow()))))
        .onErrorResume(
            failure -> {
              if (failure == reportedOperationFailure.get()
                  || failure == noIncidentOutcome.get()) {
                return Mono.error(failure);
              }
              if (publicationFailed.get()) {
                return Mono.error(failure);
              }
              ForgotPasswordCompleteIncident incident = incidentFor(failure);
              try {
                publishAndRecord(command, incident, publication, publicationFailed);
              } catch (RuntimeException publishFailure) {
                return Mono.error(publishFailure);
              }
              RuntimeException signal = new ReportedOperationFailureException();
              reportedOperationFailure.set(signal);
              return Mono.error(signal);
            });
  }

  private Mono<HydratedIdempotentResponse> executeLoaded(
      CompleteForgotPasswordCommand command,
      com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot snapshot,
      PublicationTracker publication,
      AtomicBoolean publicationFailed,
      AtomicReference<RuntimeException> reportedOperationFailure,
      AtomicReference<RuntimeException> noIncidentOutcome) {
    ForgotPasswordAuthFlowContext context = loadContext(snapshot.session().data().attributes());
    if (context == null) {
      return Mono.just(responses.toTransientResponse(invalidAuthFlow()));
    }
    return Mono.defer(
            () ->
                operation.execute(
                    new ForgotPasswordCompleteProtectedOperationInput(
                        command, context, snapshot)))
        .flatMap(
            execution -> {
              if (execution.outcome()
                  == ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL) {
                return Mono.just(responses.toTransientResponse(execution.result()));
              }
              if (!execution.incidents().isEmpty()) {
                return publishIncidentsAndSignalFailure(
                    command,
                    execution.incidents(),
                    publication,
                    publicationFailed,
                    reportedOperationFailure);
              }
              if (execution.outcome()
                  == ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE) {
                try {
                  publishAndRecord(
                      command,
                      ForgotPasswordCompleteIncident.local(
                          "COMPLETE_PROTECTED_OPERATION_FAILURE", "protected-operation"),
                      publication,
                      publicationFailed);
                } catch (RuntimeException publishFailure) {
                  return Mono.error(publishFailure);
                }
                RuntimeException signal = new ReportedOperationFailureException();
                reportedOperationFailure.set(signal);
                return Mono.error(signal);
              }
              RuntimeException signal = new NoIncidentOutcomeException();
              noIncidentOutcome.set(signal);
              return Mono.error(signal);
            });
  }

  private Mono<HydratedIdempotentResponse> publishIncidentsAndSignalFailure(
      CompleteForgotPasswordCommand command,
      List<ForgotPasswordCompleteIncident> incidents,
      PublicationTracker publication,
      AtomicBoolean publicationFailed,
      AtomicReference<RuntimeException> reportedOperationFailure) {
    try {
      for (ForgotPasswordCompleteIncident incident : incidents) {
        publishAndRecord(command, incident, publication, publicationFailed);
      }
    } catch (RuntimeException publishFailure) {
      return Mono.error(publishFailure);
    }
    RuntimeException signal = new ReportedOperationFailureException();
    reportedOperationFailure.set(signal);
    return Mono.error(signal);
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
    } catch (RuntimeException failure) {
      throw new ForgotPasswordCompletionTechnicalFailure(
          FailureOrigin.LOCAL_SYSTEM, "context-protection");
    }
  }

  private void publishAndRecord(
      CompleteForgotPasswordCommand command,
      ForgotPasswordCompleteIncident incident,
      PublicationTracker publication,
      AtomicBoolean publicationFailed) {
    try {
      failureEvents.publish(
          new FailureEvent(
              FAILURE_EVENT,
              RequestIdLogSanitizer.sanitize(command.correlationId()),
              "FORGOT_PASSWORD",
              "COMPLETE",
              incident.origin(),
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

  private CompleteForgotPasswordResult withReceipt(
      CompleteForgotPasswordResult result, PublicationTracker publication) {
    return publication.published()
        ? result.withPublicationReceipt(new ForgotPasswordCompletePublicationReceipt(true))
        : result;
  }

  private ForgotPasswordCompleteIncident incidentFor(Throwable failure) {
    if (failure instanceof ForgotPasswordCompletionTechnicalFailure technical) {
      return ForgotPasswordCompleteIncident.apim(technical.origin(), technical.stage());
    }
    return ForgotPasswordCompleteIncident.local(
        "COMPLETE_PROTECTED_OPERATION_FAILURE", "protected-operation");
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
                SOURCE_SERVER)), null);
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

  private static final class NoIncidentOutcomeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private NoIncidentOutcomeException() {
      super(null, null, false, false);
    }
  }
}
