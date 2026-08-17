package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public class CompleteRegistrationService implements CompleteRegistrationUseCase {
    private static final RegistrationIdempotencyAction ACTION = RegistrationIdempotencyAction.REGISTRATION_COMPLETE;
    private final RegistrationProtectedAuthFlowContextLoader contextLoader;
    private final AuthFlowSessionPort sessions;
    private final RegistrationUsernameCheckContextLoader usernameLoader;
    private final RegistrationPasswordValidator passwordValidator;
    private final RegistrationIdempotencyPort idempotency;
    private final RegistrationRequestHashGenerator hashes;
    private final AuthFlowSessionCompletionLockPort locks;
    private final AuthFlowSessionExpiryRefreshPort expiry;
    private final RegistrationCompletionPort completion;
    private final RegistrationLookupPort lookup;
    private final AuthFlowSessionCancellationPort cancellation;
    private final RegistrationCompletionSessionService sessionTransitions;
    private final Duration completionLockLease;
    private final Clock clock;

    public CompleteRegistrationService(RegistrationProtectedAuthFlowContextLoader contextLoader,
            AuthFlowSessionPort sessions, RegistrationUsernameCheckContextLoader usernameLoader,
            RegistrationPasswordValidator passwordValidator, RegistrationIdempotencyPort idempotency,
            RegistrationRequestHashGenerator hashes, AuthFlowSessionCompletionLockPort locks,
            AuthFlowSessionExpiryRefreshPort expiry, RegistrationCompletionPort completion,
            RegistrationLookupPort lookup, AuthFlowSessionCancellationPort cancellation, Duration completionLockLease,
            Clock clock) {
        this.contextLoader = contextLoader;
        this.sessions = sessions;
        this.usernameLoader = usernameLoader;
        this.passwordValidator = passwordValidator;
        this.idempotency = idempotency;
        this.hashes = hashes;
        this.locks = locks;
        this.expiry = expiry;
        this.completion = completion;
        this.lookup = lookup;
        this.cancellation = cancellation;
        this.completionLockLease = positiveLease(completionLockLease);
        this.clock = clock;
        this.sessionTransitions = new RegistrationCompletionSessionService(sessions);
    }

    public Mono<CompleteRegistrationResult> complete(CompleteRegistrationCommand c) {
        var contextCommand = new RegistrationProtectedAuthFlowContextCommand(c.authFlowSessionId(), c.csrfToken(),
                c.origin(), c.referer(), c.correlationId(), Set.of(AuthFlowStatus.USERNAME_VERIFIED));
        return contextLoader.load(contextCommand).flatMap(ctx -> {
            if (!ctx.isValid())
                return Mono.just(failure(
                        ctx.status() == RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID
                                ? CompleteRegistrationResultStatus.CSRF_INVALID
                                : CompleteRegistrationResultStatus.INVALID_AUTH_FLOW,
                        WorkflowNextStep.NONE,
                        com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID
                                .value()));
            return expiry.refresh(c.authFlowSessionId(), clock.instant())
                    .flatMap(session -> prepare(c, ctx.registrationContext(), session));
        });
    }

    private Mono<CompleteRegistrationResult> prepare(CompleteRegistrationCommand c, RegistrationAuthFlowContext rc,
            AuthFlowSession session) {
        if (session.status() != AuthFlowStatus.USERNAME_VERIFIED)
            return Mono.just(failure(CompleteRegistrationResultStatus.INVALID_STATE, WorkflowNextStep.PASSWORD_SETUP,
                    com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.STATE_INVALID.value()));
        UsernameCheckState uc;
        try {
            uc = usernameLoader.load(session);
        } catch (RuntimeException e) {
            return Mono.just(failure(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW, WorkflowNextStep.NONE,
                    com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID.value()));
        }
        var validation = passwordValidator.validate(c.password(), c.confirmPassword(), uc.username());
        if (!validation.valid())
            return Mono.just(new CompleteRegistrationResult(CompleteRegistrationResultStatus.VALIDATION_FAILED,
                    WorkflowNextStep.PASSWORD_SETUP, false, false, List.of(), validation.errors()));
        RegistrationRequestHash hash = hashes.hash(ACTION,
                List.of(c.authFlowSessionId().value(), sha256(c.password())));
        return idempotency.startProcessing(new RegistrationIdempotencyStartCommand(ACTION, c.idempotencyKey(), hash,
                clock.instant(), c.idempotencyTtl())).flatMap(start -> switch (start.outcome()) {
                    case STARTED -> acquire(c, rc, uc, session, hash);
                    case DUPLICATE_IN_PROGRESS ->
                        Mono.just(result(CompleteRegistrationResultStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.WAIT,
                                false, false, List.of()));
                    case COMPLETED_REPLAY -> Mono.just(result(CompleteRegistrationResultStatus.COMPLETED_REPLAY,
                            WorkflowNextStep.REGISTRATION_SUCCESS, true, false, List.of()));
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(failure(
                            CompleteRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED, WorkflowNextStep.NONE,
                            com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED
                                    .value()));
                    case FAILED_EXISTING -> Mono.just(failure(CompleteRegistrationResultStatus.SYSTEM_FAILED,
                            WorkflowNextStep.PASSWORD_SETUP,
                            com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE
                                    .value()));
                    case UNKNOWN_OUTCOME_EXISTING -> Mono.just(failure(CompleteRegistrationResultStatus.UNKNOWN_OUTCOME,
                            WorkflowNextStep.RETURN_TO_LOGIN,
                            com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.REGISTRATION_COMPLETE_OUTCOMEUNKNOWN
                                    .value()));
                });
    }

    private Mono<CompleteRegistrationResult> acquire(CompleteRegistrationCommand c, RegistrationAuthFlowContext rc,
            UsernameCheckState uc, AuthFlowSession session, RegistrationRequestHash hash) {
        return locks.acquire(c.authFlowSessionId(), completionLockLease).flatMap(handle -> {
            if (!handle.acquired())
                return Mono.just(result(CompleteRegistrationResultStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.WAIT,
                        false, false, List.of()));
            return Mono.usingWhen(
                    Mono.just(handle),
                    acquiredHandle -> sessions.load(c.authFlowSessionId())
                    .flatMap(o -> o.map(s -> invoke(c, rc, uc, s, hash)).orElseGet(() -> Mono.just(failure(
                            CompleteRegistrationResultStatus.INVALID_AUTH_FLOW, WorkflowNextStep.NONE,
                            com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID
                                    .value())))),
                    this::completionLockReleaseAfterSuccess,
                    (acquiredHandle, protectedFailure) -> completionLockReleaseAfterError(acquiredHandle),
                    this::completionLockReleaseAfterCancellation);
        });
    }

    private Mono<CompleteRegistrationResult> invoke(CompleteRegistrationCommand c, RegistrationAuthFlowContext rc,
            UsernameCheckState uc, AuthFlowSession session, RegistrationRequestHash hash) {
        if (session.status() != AuthFlowStatus.USERNAME_VERIFIED)
            return Mono.just(failure(CompleteRegistrationResultStatus.INVALID_STATE, WorkflowNextStep.PASSWORD_SETUP,
                    com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.STATE_INVALID.value()));
        return sessionTransitions.transition(session, AuthFlowStatus.COMPLETING, clock.instant(), false)
                .flatMap(completing -> {
                    SelectedTrust trust = uc.selectedTrust();
                    String mobile = rc.contactType() == RegistrationContactType.MOBILE ? rc.contactValue() : "";
                    String email = rc.contactType() == RegistrationContactType.EMAIL ? rc.contactValue() : "";
                    var downstream = new RegistrationCompletionCommand(trust.accountEnv(), trust.trustCode(),
                            uc.username(), rc.identityValue(), c.password(), rc.dateOfBirth(), mobile, email,
                            c.correlationId(),
                            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
                    return completion.complete(downstream)
                            .flatMap(r -> r.status() == RegistrationCompletionResult.Status.REGISTERED
                                    || "err.tpa.member.registered".equals(r.downstreamErrorCode())
                                            ? refreshTrusts(c, rc, completing, hash)
                                            : reject(c, completing, r.downstreamErrorCode(), hash));
                })
                .onErrorResume(e -> idempotency
                        .markUnknownOutcome(new RegistrationIdempotencyUnknownOutcomeCommand(ACTION, c.idempotencyKey(),
                                hash, clock.instant(), c.idempotencyTtl()))
                        .thenReturn(failure(CompleteRegistrationResultStatus.UNKNOWN_OUTCOME,
                                WorkflowNextStep.RETURN_TO_LOGIN,
                                com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.REGISTRATION_COMPLETE_OUTCOMEUNKNOWN
                                        .value())));
    }

    private Mono<CompleteRegistrationResult> refreshTrusts(CompleteRegistrationCommand c,
            RegistrationAuthFlowContext rc, AuthFlowSession session, RegistrationRequestHash hash) {
        var command = new RegistrationLookupCommand(rc.identityType(), rc.identityValue(), rc.dateOfBirth(),
                rc.contactType(), rc.contactValue(), com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
        return lookup.searchUnregisteredMember(command).flatMap(r -> {
            if (r.status() == RegistrationLookupStatus.ELIGIBLE && !r.accounts().isEmpty())
                return sessionTransitions
                        .transition(session, AuthFlowStatus.TRUST_SELECTION_READY, clock.instant(), true)
                        .thenReturn(result(CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
                                WorkflowNextStep.REGISTRATION_SUCCESS, true, false, r.accounts()));
            return sessionTransitions.transition(session, AuthFlowStatus.COMPLETED, clock.instant(), true)
                    .thenReturn(result(CompleteRegistrationResultStatus.REGISTERED, WorkflowNextStep.RETURN_TO_LOGIN,
                            true, true, List.of()));
        }).onErrorResume(e -> Mono.just(failure(CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED,
                WorkflowNextStep.RETURN_TO_LOGIN,
                com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.REGISTRATION_COMPLETE_CONTINUATIONUNAVAILABLE
                        .value())));
    }

    private Mono<CompleteRegistrationResult> reject(CompleteRegistrationCommand c, AuthFlowSession session, String code,
            RegistrationRequestHash hash) {
        if ("err.tpa.member.not.found".equals(code))
            return cancellation
                    .cancel(c.authFlowSessionId(), AuthFlowSessionCancellationReason.REGISTRATION_MEMBER_NOT_FOUND,
                            clock.instant())
                    .thenReturn(failure(CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
                            WorkflowNextStep.RESTART_FLOW,
                            com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.COMPLETE_MEMBER_NOT_FOUND
                                    .value()));
        if ("err.tpa.cust.id.duplicated".equals(code))
            return sessionTransitions.transition(session, AuthFlowStatus.TRUST_SELECTION_READY, clock.instant(), true)
                    .thenReturn(failure(CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
                            WorkflowNextStep.USERNAME_CHECK,
                            com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.USERNAME_ALREADY_REGISTERED
                                    .value()));
        return Mono.just(failure(CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED, WorkflowNextStep.PASSWORD_SETUP,
                com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.REGISTRATION_COMPLETE_FAILED
                        .value()));
    }

    private CompleteRegistrationResult result(CompleteRegistrationResultStatus s, WorkflowNextStep n,
            boolean registered, boolean clear, List<RegistrationLookupAccount> a) {
        return new CompleteRegistrationResult(s, n, registered, clear, a, List.of());
    }

    private CompleteRegistrationResult failure(CompleteRegistrationResultStatus s, WorkflowNextStep n, String code) {
        return new CompleteRegistrationResult(s, n, false,
                s == CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED && n == WorkflowNextStep.RESTART_FLOW,
                List.of(), List.of(new WorkflowErrorDescriptor(WorkflowErrorType.BUSINESS, code, null, List.of("PAGE"),
                        WorkflowErrorSeverity.ERROR, "SERVER")));
    }

    private static Duration positiveLease(Duration lease) {
        Objects.requireNonNull(lease, "completionLockLease must not be null");
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("completionLockLease must be positive");
        }
        return lease;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Password hash unavailable");
        }
    }

    private Mono<Void> completionLockReleaseAfterSuccess(
            com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle handle) {
        return locks.release(handle)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "completion lock release outcome was unavailable")))
                .flatMap(released -> released
                        ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException(
                                "completion lock release was not acknowledged")))
                .onErrorMap(failure -> new IllegalStateException(
                        "completion lock release outcome was unknown", failure));
    }

    private Mono<Void> completionLockReleaseAfterError(
            com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle handle) {
        return locks.release(handle)
                .onErrorResume(releaseFailure -> Mono.empty())
                .then();
    }

    private Mono<Void> completionLockReleaseAfterCancellation(
            com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle handle) {
        return locks.release(handle)
                .onErrorResume(releaseFailure -> Mono.empty())
                .then();
    }

}
