package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.*;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Idempotency-neutral acquired workflow for Registration Complete.
 *
 * <p>Callers must authorize context, refresh expiry, load protected username state, validate the
 * password, and acquire shared idempotency ownership before invoking this operation.
 */
public final class RegistrationCompleteProtectedOperation {
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionCompletionLockPort locks;
  private final RegistrationCompletionPort completion;
  private final RegistrationLookupPort lookup;
  private final AuthFlowSessionCancellationPort cancellation;
  private final RegistrationCompletionSessionService sessionTransitions;
  private final Duration completionLockLease;
  private final Clock clock;

  public RegistrationCompleteProtectedOperation(
      AuthFlowSessionPort sessions,
      AuthFlowSessionCompletionLockPort locks,
      RegistrationCompletionPort completion,
      RegistrationLookupPort lookup,
      AuthFlowSessionCancellationPort cancellation,
      Duration completionLockLease,
      Clock clock) {
    this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    this.locks = Objects.requireNonNull(locks, "locks must not be null");
    this.completion = Objects.requireNonNull(completion, "completion must not be null");
    this.lookup = Objects.requireNonNull(lookup, "lookup must not be null");
    this.cancellation = Objects.requireNonNull(cancellation, "cancellation must not be null");
    this.completionLockLease = positiveLease(completionLockLease);
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.sessionTransitions = new RegistrationCompletionSessionService(sessions);
  }

  public Mono<CompleteRegistrationResult> execute(
      RegistrationCompleteProtectedOperationInput input) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(input, "protected completion input must not be null");
          CompleteRegistrationCommand command = input.command();
          return locks
              .acquire(command.authFlowSessionId(), completionLockLease)
              .flatMap(handle -> routeLock(input, handle));
        });
  }

  private Mono<CompleteRegistrationResult> routeLock(
      RegistrationCompleteProtectedOperationInput input, SessionLockHandle handle) {
    if (!handle.acquired()) {
      return Mono.just(
          result(
              CompleteRegistrationResultStatus.DUPLICATE_IN_PROGRESS,
              WorkflowNextStep.WAIT,
              false,
              false,
              List.of()));
    }
    return Mono.usingWhen(
        Mono.just(handle),
        ignored -> reloadAndInvoke(input),
        this::completionLockReleaseAfterSuccess,
        (acquiredHandle, protectedFailure) -> completionLockReleaseAfterError(acquiredHandle),
        this::completionLockReleaseAfterCancellation);
  }

  private Mono<CompleteRegistrationResult> reloadAndInvoke(
      RegistrationCompleteProtectedOperationInput input) {
    return sessions
        .load(input.command().authFlowSessionId())
        .flatMap(
            optional ->
                optional
                    .map(session -> invoke(input, session))
                    .orElseGet(
                        () ->
                            Mono.just(
                                failure(
                                    CompleteRegistrationResultStatus.INVALID_AUTH_FLOW,
                                    WorkflowNextStep.NONE,
                                    RegistrationErrorKey.AUTH_FLOW_INVALID.value()))));
  }

  private Mono<CompleteRegistrationResult> invoke(
      RegistrationCompleteProtectedOperationInput input, AuthFlowSession session) {
    if (session.status() != AuthFlowStatus.USERNAME_VERIFIED) {
      return Mono.just(
          failure(
              CompleteRegistrationResultStatus.INVALID_STATE,
              WorkflowNextStep.PASSWORD_SETUP,
              RegistrationErrorKey.STATE_INVALID.value()));
    }
    return sessionTransitions
        .transition(session, AuthFlowStatus.COMPLETING, clock.instant(), false)
        .flatMap(completing -> dispatch(input, completing));
  }

  private Mono<CompleteRegistrationResult> dispatch(
      RegistrationCompleteProtectedOperationInput input, AuthFlowSession completing) {
    CompleteRegistrationCommand command = input.command();
    RegistrationAuthFlowContext context = input.registrationContext();
    UsernameCheckState username = input.usernameCheckState();
    SelectedTrust trust = username.selectedTrust();
    String mobile =
        context.contactType() == RegistrationContactType.MOBILE ? context.contactValue() : "";
    String email =
        context.contactType() == RegistrationContactType.EMAIL ? context.contactValue() : "";
    RegistrationCompletionCommand downstream =
        new RegistrationCompletionCommand(
            trust.accountEnv(),
            trust.trustCode(),
            username.username(),
            context.identityValue(),
            command.password(),
            context.dateOfBirth(),
            mobile,
            email,
            command.correlationId(),
            ActorUserRole.ROLE_USER_MBR);
    return completion
        .complete(downstream)
        .flatMap(
            completionResult ->
                completionResult.status() == RegistrationCompletionResult.Status.REGISTERED
                        || "err.tpa.member.registered"
                            .equals(completionResult.downstreamErrorCode())
                    ? refreshTrusts(input, completing)
                    : reject(input, completing, completionResult.downstreamErrorCode()));
  }

  private Mono<CompleteRegistrationResult> refreshTrusts(
      RegistrationCompleteProtectedOperationInput input, AuthFlowSession session) {
    RegistrationAuthFlowContext context = input.registrationContext();
    RegistrationLookupCommand command =
        new RegistrationLookupCommand(
            context.identityType(),
            context.identityValue(),
            context.dateOfBirth(),
            context.contactType(),
            context.contactValue(),
            ActorUserRole.ROLE_USER_MBR);
    return lookup
        .searchMember(command)
        .flatMap(
            result -> {
              List<RegistrationLookupAccount> remainingUnregisteredTrusts =
                  result.accounts().stream()
                      .filter(account -> Boolean.FALSE.equals(account.registered()))
                      .toList();
              if (result.status() == RegistrationLookupStatus.ELIGIBLE
                  && !remainingUnregisteredTrusts.isEmpty()) {
                return sessionTransitions
                    .transition(
                        session, AuthFlowStatus.TRUST_SELECTION_READY, clock.instant(), true)
                    .thenReturn(
                        result(
                            CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
                            WorkflowNextStep.SELECT_TRUST,
                            true,
                            false,
                            remainingUnregisteredTrusts));
              }
              return sessionTransitions
                  .transition(session, AuthFlowStatus.COMPLETED, clock.instant(), true)
                  .thenReturn(
                      result(
                          CompleteRegistrationResultStatus.REGISTERED,
                          WorkflowNextStep.RETURN_TO_LOGIN,
                          true,
                          true,
                          List.of()));
            })
        .onErrorResume(
            ignored ->
                Mono.just(
                    failure(
                        CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED,
                        WorkflowNextStep.RETURN_TO_LOGIN,
                        RegistrationErrorKey.REGISTRATION_COMPLETE_CONTINUATIONUNAVAILABLE
                            .value())));
  }

  private Mono<CompleteRegistrationResult> reject(
      RegistrationCompleteProtectedOperationInput input,
      AuthFlowSession session,
      String downstreamCode) {
    if ("err.tpa.member.not.found".equals(downstreamCode)) {
      return cancellation
          .cancel(
              input.command().authFlowSessionId(),
              AuthFlowSessionCancellationReason.REGISTRATION_MEMBER_NOT_FOUND,
              clock.instant())
          .thenReturn(
              failure(
                  CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
                  WorkflowNextStep.RESTART_FLOW,
                  RegistrationErrorKey.COMPLETE_MEMBER_NOT_FOUND.value()));
    }
    if ("err.tpa.cust.id.duplicated".equals(downstreamCode)) {
      return sessionTransitions
          .transition(session, AuthFlowStatus.TRUST_SELECTION_READY, clock.instant(), true)
          .thenReturn(
              failure(
                  CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
                  WorkflowNextStep.USERNAME_CHECK,
                  RegistrationErrorKey.USERNAME_ALREADY_REGISTERED.value()));
    }
    return Mono.just(
        failure(
            CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
            WorkflowNextStep.PASSWORD_SETUP,
            RegistrationErrorKey.REGISTRATION_COMPLETE_FAILED.value()));
  }

  private CompleteRegistrationResult result(
      CompleteRegistrationResultStatus status,
      WorkflowNextStep nextStep,
      boolean registered,
      boolean clearAuthFlowCookie,
      List<RegistrationLookupAccount> remainingTrusts) {
    return new CompleteRegistrationResult(
        status, nextStep, registered, clearAuthFlowCookie, remainingTrusts, List.of());
  }

  private CompleteRegistrationResult failure(
      CompleteRegistrationResultStatus status, WorkflowNextStep nextStep, String code) {
    return new CompleteRegistrationResult(
        status,
        nextStep,
        false,
        status == CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED
            && nextStep == WorkflowNextStep.RESTART_FLOW,
        List.of(),
        List.of(
            new WorkflowErrorDescriptor(
                WorkflowErrorType.BUSINESS,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                "SERVER")));
  }

  private Mono<Void> completionLockReleaseAfterSuccess(SessionLockHandle handle) {
    return locks
        .release(handle)
        .switchIfEmpty(
            Mono.error(
                new IllegalStateException("completion lock release outcome was unavailable")))
        .flatMap(
            released ->
                released
                    ? Mono.<Void>empty()
                    : Mono.error(
                        new IllegalStateException("completion lock release was not acknowledged")))
        .onErrorMap(
            failure ->
                new IllegalStateException("completion lock release outcome was unknown", failure));
  }

  private Mono<Void> completionLockReleaseAfterError(SessionLockHandle handle) {
    return locks.release(handle).onErrorResume(releaseFailure -> Mono.empty()).then();
  }

  private Mono<Void> completionLockReleaseAfterCancellation(SessionLockHandle handle) {
    return locks.release(handle).onErrorResume(releaseFailure -> Mono.empty()).then();
  }

  private static Duration positiveLease(Duration lease) {
    Objects.requireNonNull(lease, "completionLockLease must not be null");
    if (lease.isZero() || lease.isNegative()) {
      throw new IllegalArgumentException("completionLockLease must be positive");
    }
    return lease;
  }
}
