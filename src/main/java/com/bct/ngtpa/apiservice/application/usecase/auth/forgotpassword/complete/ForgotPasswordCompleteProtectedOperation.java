package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidationStatus;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Idempotency-neutral protected business operation for Forgot Password COMPLETE. */
public final class ForgotPasswordCompleteProtectedOperation {
  private static final String SOURCE_SERVER = "SERVER";
  private static final String DOWNSTREAM_PASSWORD_INVALID = "err.tpa.password.invalid";
  private static final String DOWNSTREAM_PASSWORD_REUSED =
      "err.tpa.password.used.in.last.5.changes.";
  private static final String DOWNSTREAM_MEMBER_NOT_FOUND = "err.tpa.member.not.found";
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionMutationLockPort locks;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final ForgotPasswordCompletionPort completion;
  private final ForgotPasswordResetAuthorizationValidator authorizationValidator;
  private final ForgotPasswordPasswordValidator passwordValidator;
  private final Clock clock;
  private final Duration lockLease;

  public ForgotPasswordCompleteProtectedOperation(
      AuthFlowSessionPort sessions,
      AuthFlowSessionMutationLockPort locks,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      ForgotPasswordCompletionPort completion,
      ForgotPasswordResetAuthorizationValidator authorizationValidator,
      ForgotPasswordPasswordValidator passwordValidator,
      Clock clock,
      Duration lockLease) {
    this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    this.locks = Objects.requireNonNull(locks, "locks must not be null");
    this.protector = Objects.requireNonNull(protector, "protector must not be null");
    this.completion = Objects.requireNonNull(completion, "completion must not be null");
    this.authorizationValidator = Objects.requireNonNull(authorizationValidator);
    this.passwordValidator = Objects.requireNonNull(passwordValidator);
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.lockLease = requirePositive(lockLease);
  }

  public Mono<ForgotPasswordCompleteProtectedExecutionResult> execute(
      ForgotPasswordCompleteProtectedOperationInput input) {
    Objects.requireNonNull(input, "protected completion input must not be null");
    return locks
        .acquire(input.command().authFlowSessionId(), lockLease)
        .flatMap(handle -> route(input, handle));
  }

  private Mono<ForgotPasswordCompleteProtectedExecutionResult> route(
      ForgotPasswordCompleteProtectedOperationInput input, SessionLockHandle handle) {
    if (!handle.acquired()) {
      return Mono.just(ForgotPasswordCompleteProtectedExecutionResult.terminal(duplicate()));
    }
    return Mono.usingWhen(
        Mono.just(handle),
        ignored -> invoke(input),
        this::releaseAfterSuccess,
        (held, failure) -> releaseAfterFailure(held),
        this::releaseAfterCancellation);
  }

  private Mono<ForgotPasswordCompleteProtectedExecutionResult> invoke(
      ForgotPasswordCompleteProtectedOperationInput input) {
    CompleteForgotPasswordCommand command = input.command();
    AuthFlowSession session = input.sessionSnapshot().session();
    ForgotPasswordAuthFlowContext context = input.context();
    Instant now = clock.instant();
    ForgotPasswordResetAuthorizationValidationStatus authorizationStatus =
        authorizationValidator
            .validate(
                context.resetAuthorization(),
                session,
                command.authFlowSessionId(),
                context.resetSubjectId(),
                now)
            .status();
    if (authorizationStatus
        == ForgotPasswordResetAuthorizationValidationStatus.UNKNOWN_OUTCOME_BLOCKED) {
      return Mono.just(
          ForgotPasswordCompleteProtectedExecutionResult.unknownOutcome(unknownOutcome()));
    }
    if (authorizationStatus != ForgotPasswordResetAuthorizationValidationStatus.VALID) {
      return Mono.just(ForgotPasswordCompleteProtectedExecutionResult.terminal(invalidAuthFlow()));
    }
    ForgotPasswordPasswordValidationResult passwordValidation =
        passwordValidator.validate(
            command.password(), command.confirmPassword(), context.username());
    if (!passwordValidation.valid()) {
      return Mono.just(
          ForgotPasswordCompleteProtectedExecutionResult.terminal(
              new CompleteForgotPasswordResult(
                  CompleteForgotPasswordStatus.VALIDATION_FAILED,
                  WorkflowNextStep.PASSWORD_SETUP,
                  false,
                  false,
                  passwordValidation.errors())));
    }
    return completion
        .complete(
            new ForgotPasswordCompletionCommand(
                context.username(),
                command.password(),
                command.correlationId(),
                command.actorUserRole()))
        .flatMap(result -> classify(session, context, result, now))
        .onErrorResume(ignored -> blockUnknown(session, now));
  }

  private Mono<ForgotPasswordCompleteProtectedExecutionResult> classify(
      AuthFlowSession session,
      ForgotPasswordAuthFlowContext context,
      ForgotPasswordCompletionResult result,
      Instant now) {
    if (result == null
        || result.status() == ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE) {
      return Mono.just(
          ForgotPasswordCompleteProtectedExecutionResult.technicalFailure(systemFailed()));
    }
    if (result.status() == ForgotPasswordCompletionResult.Status.UNKNOWN_OUTCOME) {
      return blockUnknown(session, now);
    }
    if (result.status() == ForgotPasswordCompletionResult.Status.REJECTED) {
      if (DOWNSTREAM_PASSWORD_INVALID.equals(result.downstreamErrorCode())) {
        return Mono.just(
            ForgotPasswordCompleteProtectedExecutionResult.terminal(passwordInvalid()));
      }
      if (DOWNSTREAM_PASSWORD_REUSED.equals(result.downstreamErrorCode())) {
        return Mono.just(ForgotPasswordCompleteProtectedExecutionResult.terminal(passwordReused()));
      }
      if (DOWNSTREAM_MEMBER_NOT_FOUND.equals(result.downstreamErrorCode())) {
        return updateSession(session, AuthFlowStatus.COMPLETED, context, now)
            .thenReturn(ForgotPasswordCompleteProtectedExecutionResult.terminal(memberNotFound()))
            .onErrorResume(ignored -> blockUnknown(session, now));
      }
      return Mono.just(
          ForgotPasswordCompleteProtectedExecutionResult.technicalFailure(systemFailed()));
    }
    if (result.status() != ForgotPasswordCompletionResult.Status.RESET) {
      return Mono.just(
          ForgotPasswordCompleteProtectedExecutionResult.technicalFailure(systemFailed()));
    }
    ForgotPasswordResetAuthorization authorization = context.resetAuthorization();
    ForgotPasswordResetAuthorization consumed =
        new ForgotPasswordResetAuthorization(
            authorization.authorizationId(),
            authorization.authFlowSessionId(),
            authorization.resetSubjectId(),
            authorization.issuedAt(),
            authorization.expiresAt(),
            now);
    ForgotPasswordAuthFlowContext updatedContext =
        context
            .withResetAuthorization(consumed)
            .withPhase(ForgotPasswordProtectedPhase.PASSWORD_SETUP);
    return updateStatus(session, AuthFlowStatus.COMPLETING, now)
        .flatMap(
            completing -> updateSession(completing, AuthFlowStatus.COMPLETED, updatedContext, now))
        .thenReturn(ForgotPasswordCompleteProtectedExecutionResult.terminal(success()))
        .onErrorResume(ignored -> blockUnknown(session, now));
  }

  private Mono<ForgotPasswordCompleteProtectedExecutionResult> blockUnknown(
      AuthFlowSession session, Instant now) {
    return updateStatus(session, AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED, now)
        .onErrorResume(ignored -> Mono.empty())
        .thenReturn(
            ForgotPasswordCompleteProtectedExecutionResult.unknownOutcome(unknownOutcome()));
  }

  private Mono<AuthFlowSession> updateStatus(
      AuthFlowSession session, AuthFlowStatus status, Instant now) {
    AuthFlowSession updated =
        new AuthFlowSession(
            session.authFlowSessionId(),
            session.scenario(),
            status,
            session.csrfTokenHash(),
            session.activeOtpChallengeId(),
            session.data(),
            session.createdAt(),
            now,
            session.expiresAt());
    return new RevisionAwareAuthFlowSessionWriter(sessions).updateExpected(session, updated);
  }

  private Mono<AuthFlowSession> updateSession(
      AuthFlowSession session,
      AuthFlowStatus status,
      ForgotPasswordAuthFlowContext context,
      Instant now) {
    ForgotPasswordAuthFlowEncryptedContext encrypted = protector.protect(context);
    Map<String, String> attributes = new LinkedHashMap<>(session.data().attributes());
    attributes.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
    attributes.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
    attributes.put(CONTEXT_ALGORITHM, encrypted.algorithm());
    AuthFlowSession updated =
        new AuthFlowSession(
            session.authFlowSessionId(),
            session.scenario(),
            status,
            session.csrfTokenHash(),
            null,
            new AuthFlowSessionData(
                context.contactChannel(), context.maskedDestination(), attributes),
            session.createdAt(),
            now,
            session.expiresAt());
    return new RevisionAwareAuthFlowSessionWriter(sessions).updateExpected(session, updated);
  }

  private CompleteForgotPasswordResult success() {
    return result(
        CompleteForgotPasswordStatus.SUCCESS,
        WorkflowNextStep.RETURN_TO_LOGIN,
        true,
        true,
        List.of());
  }

  private CompleteForgotPasswordResult passwordInvalid() {
    return result(
        CompleteForgotPasswordStatus.BUSINESS_REJECTED,
        WorkflowNextStep.PASSWORD_SETUP,
        false,
        false,
        List.of(
            error(
                WorkflowErrorType.BUSINESS,
                ForgotPasswordCompleteErrorKeys.PASSWORD_INVALID,
                "password")));
  }

  private CompleteForgotPasswordResult passwordReused() {
    return result(
        CompleteForgotPasswordStatus.BUSINESS_REJECTED,
        WorkflowNextStep.PASSWORD_SETUP,
        false,
        false,
        List.of(
            error(
                WorkflowErrorType.BUSINESS,
                ForgotPasswordCompleteErrorKeys.PASSWORD_REUSED,
                "password")));
  }

  private CompleteForgotPasswordResult memberNotFound() {
    return result(
        CompleteForgotPasswordStatus.BUSINESS_REJECTED,
        WorkflowNextStep.RETURN_TO_LOGIN,
        true,
        true,
        List.of(
            error(
                WorkflowErrorType.BUSINESS,
                ForgotPasswordCompleteErrorKeys.MEMBER_NOT_FOUND,
                "PAGE")));
  }

  private CompleteForgotPasswordResult invalidAuthFlow() {
    return result(
        CompleteForgotPasswordStatus.INVALID_AUTH_FLOW,
        WorkflowNextStep.NONE,
        false,
        false,
        List.of(
            error(
                WorkflowErrorType.FORM,
                ForgotPasswordCompleteErrorKeys.AUTH_FLOW_INVALID,
                "PAGE")));
  }

  private CompleteForgotPasswordResult duplicate() {
    return result(
        CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS,
        WorkflowNextStep.WAIT,
        false,
        false,
        List.of());
  }

  private CompleteForgotPasswordResult systemFailed() {
    return result(
        CompleteForgotPasswordStatus.SYSTEM_FAILED,
        WorkflowNextStep.PASSWORD_SETUP,
        false,
        false,
        List.of(
            error(
                WorkflowErrorType.SYSTEM,
                ForgotPasswordCompleteErrorKeys.SYSTEM_UNAVAILABLE,
                "PAGE")));
  }

  private CompleteForgotPasswordResult unknownOutcome() {
    return result(
        CompleteForgotPasswordStatus.UNKNOWN_OUTCOME,
        WorkflowNextStep.NONE,
        false,
        false,
        List.of(
            error(
                WorkflowErrorType.SYSTEM,
                ForgotPasswordCompleteErrorKeys.SYSTEM_UNAVAILABLE,
                "PAGE")));
  }

  private CompleteForgotPasswordResult result(
      CompleteForgotPasswordStatus status,
      WorkflowNextStep nextStep,
      boolean clearAuthFlowCookie,
      boolean clearXsrfCookie,
      List<WorkflowErrorDescriptor> errors) {
    return new CompleteForgotPasswordResult(
        status, nextStep, clearAuthFlowCookie, clearXsrfCookie, errors);
  }

  private WorkflowErrorDescriptor error(WorkflowErrorType type, String code, String target) {
    return new WorkflowErrorDescriptor(
        type, code, null, List.of(target), WorkflowErrorSeverity.ERROR, SOURCE_SERVER);
  }

  private Mono<Void> releaseAfterSuccess(SessionLockHandle handle) {
    return locks.release(handle).onErrorReturn(false).then();
  }

  private Mono<Void> releaseAfterFailure(SessionLockHandle handle) {
    return locks.release(handle).onErrorResume(ignored -> Mono.empty()).then();
  }

  private Mono<Void> releaseAfterCancellation(SessionLockHandle handle) {
    return locks.release(handle).onErrorResume(ignored -> Mono.empty()).then();
  }

  private static Duration requirePositive(Duration value) {
    Objects.requireNonNull(value, "lockLease must not be null");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("lockLease must be positive");
    }
    return value;
  }
}
