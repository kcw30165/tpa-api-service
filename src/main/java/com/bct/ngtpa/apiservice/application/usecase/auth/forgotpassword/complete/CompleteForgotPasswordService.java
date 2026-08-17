package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidationStatus;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidator;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidationStatus;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class CompleteForgotPasswordService implements CompleteForgotPasswordUseCase {
    private static final ForgotPasswordIdempotencyAction ACTION = ForgotPasswordIdempotencyAction.COMPLETE;
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_SUCCESS_PENDING_FINALIZATION = "SUCCESS_PENDING_FINALIZATION";
    private static final String OUTCOME_PASSWORD_INVALID = "PASSWORD_INVALID";
    private static final String OUTCOME_MEMBER_NOT_FOUND = "MEMBER_NOT_FOUND";
    private static final String OUTCOME_MEMBER_NOT_FOUND_PENDING_FINALIZATION = "MEMBER_NOT_FOUND_PENDING_FINALIZATION";
    private static final String SOURCE_SERVER = "SERVER";

    private final AuthFlowSessionPort sessions;
    private final AuthFlowSessionMutationLockPort locks;
    private final ForgotPasswordAuthFlowContextProtectorPort protector;
    private final ForgotPasswordCompletionPort completion;
    private final ForgotPasswordIdempotencyPort idempotency;
    private final ForgotPasswordResetAuthorizationValidator authorizationValidator;
    private final ForgotPasswordPasswordValidator passwordValidator;
    private final Clock clock;
    private final Duration idempotencyTtl;
    private final Duration lockLease;
    private final List<String> allowedOrigins;
    private final CsrfTokenValidationService csrfValidator = new CsrfTokenValidationService(
            new Sha256CsrfTokenHasher());
    private final OriginRefererValidator originRefererValidator = new OriginRefererValidator();

    public CompleteForgotPasswordService(
            AuthFlowSessionPort sessions,
            AuthFlowSessionMutationLockPort locks,
            ForgotPasswordAuthFlowContextProtectorPort protector,
            ForgotPasswordCompletionPort completion,
            ForgotPasswordIdempotencyPort idempotency,
            ForgotPasswordResetAuthorizationValidator authorizationValidator,
            ForgotPasswordPasswordValidator passwordValidator,
            Clock clock,
            Duration idempotencyTtl,
            Duration lockLease,
            List<String> allowedOrigins) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.locks = Objects.requireNonNull(locks, "locks must not be null");
        this.protector = Objects.requireNonNull(protector, "protector must not be null");
        this.completion = Objects.requireNonNull(completion, "completion must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
        this.authorizationValidator = Objects.requireNonNull(
                authorizationValidator,
                "authorizationValidator must not be null");
        this.passwordValidator = Objects.requireNonNull(passwordValidator, "passwordValidator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.idempotencyTtl = requirePositive(idempotencyTtl, "idempotencyTtl");
        this.lockLease = requirePositive(lockLease, "lockLease");
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    @Override
    public Mono<CompleteForgotPasswordResult> complete(CompleteForgotPasswordCommand command) {
        if (command == null || command.authFlowSessionId() == null) {
            return Mono.just(invalidAuthFlow());
        }
        Instant now = Instant.now(clock);
        ForgotPasswordRequestHash requestHash = requestHash();
        Duration ttl = command.idempotencyTtl() == null ? idempotencyTtl : command.idempotencyTtl();

        return idempotency.startProcessing(new ForgotPasswordIdempotencyStartCommand(
                ACTION,
                command.idempotencyKey(),
                requestHash,
                now,
                now.plus(ttl),
                ttl)).flatMap(start -> switch (start.outcome()) {
                    case STARTED -> acquireAndRun(command, requestHash, start.record(), false, ttl);
                    case COMPLETED_REPLAY -> resolveReplay(command, requestHash, start.record(), ttl);
                    case DUPLICATE_IN_PROGRESS -> Mono.just(duplicateInProgress());
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(idempotencyKeyReused());
                    case FAILED_EXISTING -> Mono.just(systemFailed(WorkflowNextStep.PASSWORD_SETUP));
                    case UNKNOWN_OUTCOME_EXISTING -> Mono.just(unknownOutcome());
                }).onErrorReturn(systemFailed(WorkflowNextStep.NONE));
    }

    private Mono<CompleteForgotPasswordResult> resolveReplay(
            CompleteForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            ForgotPasswordIdempotencyRecord record,
            Duration ttl) {
        ForgotPasswordIdempotencyReplaySnapshot snapshot = record == null ? null : record.replaySnapshot();
        if (snapshot == null || snapshot.outcomeCode() == null) {
            return Mono.just(systemFailed(WorkflowNextStep.NONE));
        }
        return switch (snapshot.outcomeCode()) {
            case OUTCOME_SUCCESS -> Mono.just(success());
            case OUTCOME_PASSWORD_INVALID -> Mono.just(passwordInvalidResult());
            case OUTCOME_MEMBER_NOT_FOUND -> Mono.just(memberNotFoundResult());
            case OUTCOME_SUCCESS_PENDING_FINALIZATION,
                    OUTCOME_MEMBER_NOT_FOUND_PENDING_FINALIZATION ->
                acquireAndRun(command, requestHash, record, true, ttl);
            default -> Mono.just(systemFailed(WorkflowNextStep.NONE));
        };
    }

    private Mono<CompleteForgotPasswordResult> acquireAndRun(
            CompleteForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            ForgotPasswordIdempotencyRecord record,
            boolean replayCleanup,
            Duration ttl) {
        return locks.acquire(command.authFlowSessionId(), lockLease)
                .flatMap(handle -> {
                    if (!handle.acquired()) {
                        return Mono.just(duplicateInProgress());
                    }
                    return Mono.usingWhen(
                            Mono.just(handle),
                            ignored -> sessions.loadSnapshot(command.authFlowSessionId())
                                    .flatMap(optional -> optional
                                            .map(snapshot -> replayCleanup
                                                    ? cleanupReplay(command, requestHash, record, snapshot, ttl)
                                                    : completeValidatedRequest(command, requestHash, snapshot, ttl))
                                            .orElseGet(() -> Mono.just(invalidAuthFlow()))),
                            this::releaseAfterSuccess,
                            (ignored, error) -> releaseAfterFailure(handle),
                            this::releaseAfterCancellation);
                })
                .onErrorReturn(systemFailed(WorkflowNextStep.NONE));
    }

    private Mono<CompleteForgotPasswordResult> completeValidatedRequest(
            CompleteForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            AuthFlowSessionSnapshot snapshot,
            Duration ttl) {
        Instant now = Instant.now(clock);
        LoadedContext loaded = validateForCompletion(snapshot.session(), command, now);
        if (loaded.failure() != null) {
            return Mono.just(loaded.failure());
        }

        ForgotPasswordPasswordValidationResult passwordValidation = passwordValidator.validate(
                command.password(),
                command.confirmPassword(),
                loaded.context().normalizedUsername());
        if (!passwordValidation.valid()) {
            return Mono.just(new CompleteForgotPasswordResult(
                    CompleteForgotPasswordStatus.VALIDATION_FAILED,
                    WorkflowNextStep.PASSWORD_SETUP,
                    false,
                    false,
                    passwordValidation.errors()));
        }

        return completion.complete(
                new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand(
                        loaded.context().customerId(),
                        command.password(),
                        command.correlationId(),
                        loaded.context().accountEnv(),
                        command.actorUserRole()))
                .flatMap(result -> handleDownstream(command, requestHash, snapshot.session(), loaded.context(), result,
                        now, ttl))
                .onErrorResume(error -> markUnknownOutcome(command, requestHash, snapshot.session(), now, ttl));
    }

    private Mono<CompleteForgotPasswordResult> cleanupReplay(
            CompleteForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            ForgotPasswordIdempotencyRecord record,
            AuthFlowSessionSnapshot snapshot,
            Duration ttl) {
        Instant now = Instant.now(clock);
        ForgotPasswordIdempotencyReplaySnapshot replaySnapshot = record == null ? null : record.replaySnapshot();
        if (replaySnapshot == null) {
            return Mono.just(systemFailed(WorkflowNextStep.NONE));
        }
        LoadedContext loaded = validateReplay(snapshot.session(), command, now);
        if (loaded.failure() != null) {
            return Mono.just(loaded.failure());
        }
        return switch (replaySnapshot.outcomeCode()) {
            case OUTCOME_SUCCESS_PENDING_FINALIZATION -> finalizeSuccess(
                    command.idempotencyKey(),
                    requestHash,
                    snapshot.session(),
                    loaded.context(),
                    now,
                    ttl);
            case OUTCOME_MEMBER_NOT_FOUND_PENDING_FINALIZATION -> finalizeMemberNotFound(
                    command.idempotencyKey(),
                    requestHash,
                    snapshot.session(),
                    loaded.context(),
                    now,
                    ttl);
            default -> Mono.just(systemFailed(WorkflowNextStep.NONE));
        };
    }

    private Mono<CompleteForgotPasswordResult> handleDownstream(
            CompleteForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            AuthFlowSession session,
            ForgotPasswordAuthFlowContext context,
            ForgotPasswordCompletionResult result,
            Instant now,
            Duration ttl) {
        if (result == null) {
            return markFailed(command.idempotencyKey(), requestHash, now, ttl);
        }
        return switch (result.status()) {
            case RESET -> markCompleting(session, now)
                    .flatMap(completing -> markCompleted(
                            command.idempotencyKey(),
                            requestHash,
                            replaySnapshot(OUTCOME_SUCCESS_PENDING_FINALIZATION),
                            now,
                            ttl).thenReturn(completing))
                    .flatMap(completing -> finalizeSuccess(
                            command.idempotencyKey(),
                            requestHash,
                            completing,
                            context,
                            now,
                            ttl))
                    .onErrorReturn(systemFailed(WorkflowNextStep.NONE));
            case REJECTED -> handleRejection(command.idempotencyKey(), requestHash, session, context, result, now, ttl);
            case TECHNICAL_FAILURE -> markFailed(command.idempotencyKey(), requestHash, now, ttl);
            case UNKNOWN_OUTCOME -> markUnknownOutcome(command, requestHash, session, now, ttl);
        };
    }

    private Mono<CompleteForgotPasswordResult> handleRejection(
            ForgotPasswordIdempotencyKey idempotencyKey,
            ForgotPasswordRequestHash requestHash,
            AuthFlowSession session,
            ForgotPasswordAuthFlowContext context,
            ForgotPasswordCompletionResult result,
            Instant now,
            Duration ttl) {
        if (ForgotPasswordCompleteErrorKeys.PASSWORD_INVALID.equals(result.downstreamErrorCode())) {
            return markCompleted(idempotencyKey, requestHash, replaySnapshot(OUTCOME_PASSWORD_INVALID), now, ttl)
                    .thenReturn(passwordInvalidResult());
        }
        if (ForgotPasswordCompleteErrorKeys.MEMBER_NOT_FOUND.equals(result.downstreamErrorCode())) {
            return markCompleting(session, now)
                    .flatMap(completing -> markCompleted(
                            idempotencyKey,
                            requestHash,
                            replaySnapshot(OUTCOME_MEMBER_NOT_FOUND_PENDING_FINALIZATION),
                            now,
                            ttl).thenReturn(completing))
                    .flatMap(completing -> finalizeMemberNotFound(
                            idempotencyKey,
                            requestHash,
                            completing,
                            context,
                            now,
                            ttl))
                    .onErrorReturn(systemFailed(WorkflowNextStep.NONE));
        }
        return markFailed(idempotencyKey, requestHash, now, ttl);
    }

    private Mono<CompleteForgotPasswordResult> finalizeSuccess(
            ForgotPasswordIdempotencyKey idempotencyKey,
            ForgotPasswordRequestHash requestHash,
            AuthFlowSession session,
            ForgotPasswordAuthFlowContext context,
            Instant now,
            Duration ttl) {
        Mono<AuthFlowSession> finalizeSession;
        if (session.status() == AuthFlowStatus.COMPLETED) {
            finalizeSession = Mono.just(session);
        } else {
            ForgotPasswordResetAuthorization authorization = context.resetAuthorization();
            ForgotPasswordResetAuthorization consumedAuthorization = new ForgotPasswordResetAuthorization(
                    authorization.authorizationId(),
                    authorization.authFlowSessionId(),
                    authorization.resetSubjectId(),
                    authorization.issuedAt(),
                    authorization.expiresAt(),
                    now);
            ForgotPasswordAuthFlowContext updatedContext = context.withResetAuthorization(consumedAuthorization)
                    .withPhase(ForgotPasswordProtectedPhase.PASSWORD_SETUP);
            finalizeSession = updateSession(session, AuthFlowStatus.COMPLETED, updatedContext, now);
        }
        return finalizeSession
                .flatMap(ignored -> markCompleted(
                        idempotencyKey,
                        requestHash,
                        replaySnapshot(OUTCOME_SUCCESS),
                        now,
                        ttl))
                .thenReturn(success())
                .onErrorReturn(systemFailed(WorkflowNextStep.NONE));
    }

    private Mono<CompleteForgotPasswordResult> finalizeMemberNotFound(
            ForgotPasswordIdempotencyKey idempotencyKey,
            ForgotPasswordRequestHash requestHash,
            AuthFlowSession session,
            ForgotPasswordAuthFlowContext context,
            Instant now,
            Duration ttl) {
        Mono<AuthFlowSession> finalizeSession = session.status() == AuthFlowStatus.COMPLETED
                ? Mono.just(session)
                : updateSession(session, AuthFlowStatus.COMPLETED, context, now);
        return finalizeSession
                .flatMap(ignored -> markCompleted(
                        idempotencyKey,
                        requestHash,
                        replaySnapshot(OUTCOME_MEMBER_NOT_FOUND),
                        now,
                        ttl))
                .thenReturn(memberNotFoundResult())
                .onErrorReturn(systemFailed(WorkflowNextStep.NONE));
    }

    private Mono<CompleteForgotPasswordResult> markFailed(
            ForgotPasswordIdempotencyKey idempotencyKey,
            ForgotPasswordRequestHash requestHash,
            Instant now,
            Duration ttl) {
        return idempotency.markFailed(new ForgotPasswordIdempotencyFailCommand(
                ACTION,
                idempotencyKey,
                requestHash,
                now,
                ttl)).thenReturn(systemFailed(WorkflowNextStep.PASSWORD_SETUP))
                .onErrorReturn(systemFailed(WorkflowNextStep.PASSWORD_SETUP));
    }

    private Mono<CompleteForgotPasswordResult> markUnknownOutcome(
            CompleteForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            AuthFlowSession session,
            Instant now,
            Duration ttl) {
        return markUnknownOutcomeState(session, now)
                .onErrorResume(error -> Mono.empty())
                .then(idempotency.markUnknownOutcome(new ForgotPasswordIdempotencyUnknownOutcomeCommand(
                        ACTION,
                        command.idempotencyKey(),
                        requestHash,
                        now,
                        ttl)).onErrorResume(error -> Mono.empty()))
                .thenReturn(unknownOutcome());
    }

    private LoadedContext validateForCompletion(AuthFlowSession session, CompleteForgotPasswordCommand command,
            Instant now) {
        CompleteForgotPasswordResult requestValidation = validateSessionTransport(session, command, now);
        if (requestValidation != null) {
            return LoadedContext.failure(requestValidation);
        }
        if (session.status() != AuthFlowStatus.RESET_AUTHORIZED) {
            return LoadedContext.failure(invalidAuthFlow());
        }
        ForgotPasswordAuthFlowContext context = loadContext(session);
        if (context == null
                || context.phase() != ForgotPasswordProtectedPhase.PASSWORD_SETUP
                || context.resetAuthorization() == null
                || blank(context.resetSubjectId())) {
            return LoadedContext.failure(invalidAuthFlow());
        }
        ForgotPasswordResetAuthorizationValidationStatus status = authorizationValidator.validate(
                context.resetAuthorization(),
                session,
                command.authFlowSessionId(),
                context.resetSubjectId(),
                now).status();
        if (status == ForgotPasswordResetAuthorizationValidationStatus.UNKNOWN_OUTCOME_BLOCKED) {
            return LoadedContext.failure(unknownOutcome());
        }
        if (status != ForgotPasswordResetAuthorizationValidationStatus.VALID) {
            return LoadedContext.failure(invalidAuthFlow());
        }
        return LoadedContext.success(context);
    }

    private LoadedContext validateReplay(AuthFlowSession session, CompleteForgotPasswordCommand command, Instant now) {
        CompleteForgotPasswordResult requestValidation = validateSessionTransport(session, command, now);
        if (requestValidation != null) {
            return LoadedContext.failure(requestValidation);
        }
        if (session.status() != AuthFlowStatus.COMPLETING && session.status() != AuthFlowStatus.COMPLETED) {
            return LoadedContext.failure(invalidAuthFlow());
        }
        ForgotPasswordAuthFlowContext context = loadContext(session);
        return context == null ? LoadedContext.failure(invalidAuthFlow()) : LoadedContext.success(context);
    }

    private CompleteForgotPasswordResult validateSessionTransport(
            AuthFlowSession session,
            CompleteForgotPasswordCommand command,
            Instant now) {
        if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD) {
            return invalidAuthFlow();
        }
        if (csrfValidator.validate(command.csrfToken(), session.csrfTokenHash())
                .status() != CsrfTokenValidationStatus.VALID) {
            return csrfInvalid();
        }
        if (!allowedOrigins.isEmpty()) {
            OriginRefererValidationStatus originStatus = originRefererValidator.validate(
                    command.origin(),
                    command.referer(),
                    allowedOrigins).status();
            if (originStatus != OriginRefererValidationStatus.VALID) {
                return csrfInvalid();
            }
        }
        if (session.status() == AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED) {
            return unknownOutcome();
        }
        if (session.isExpired(now) || session.isTerminal() && session.status() != AuthFlowStatus.COMPLETED) {
            return invalidAuthFlow();
        }
        return null;
    }

    private ForgotPasswordAuthFlowContext loadContext(AuthFlowSession session) {
        Map<String, String> attributes = session.data() == null || session.data().attributes() == null
                ? Map.of()
                : session.data().attributes();
        String ciphertext = attributes.get(CONTEXT_CIPHERTEXT);
        String keyVersion = attributes.get(CONTEXT_KEY_VERSION);
        String algorithm = attributes.get(CONTEXT_ALGORITHM);
        if (blank(ciphertext) || blank(keyVersion)) {
            return null;
        }
        try {
            return protector.unprotect(new ForgotPasswordAuthFlowEncryptedContext(
                    ciphertext,
                    keyVersion,
                    blank(algorithm) ? ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM : algorithm));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Mono<AuthFlowSession> markCompleting(AuthFlowSession session, Instant now) {
        if (session.status() == AuthFlowStatus.COMPLETING || session.status() == AuthFlowStatus.COMPLETED) {
            return Mono.just(session);
        }
        return updateStatus(session, AuthFlowStatus.COMPLETING, now);
    }

    private Mono<Void> markUnknownOutcomeState(AuthFlowSession session, Instant now) {
        if (session.status() == AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED) {
            return Mono.empty();
        }
        return updateStatus(session, AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED, now).then();
    }

    private Mono<AuthFlowSession> updateStatus(AuthFlowSession session, AuthFlowStatus status, Instant now) {
        AuthFlowSession updated = new AuthFlowSession(
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
        AuthFlowSession updated = new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                status,
                session.csrfTokenHash(),
                null,
                new AuthFlowSessionData(context.contactChannel(), context.maskedDestination(), attributes),
                session.createdAt(),
                now,
                session.expiresAt());
        return new RevisionAwareAuthFlowSessionWriter(sessions).updateExpected(session, updated);
    }

    private Mono<ForgotPasswordIdempotencyRecord> markCompleted(
            ForgotPasswordIdempotencyKey idempotencyKey,
            ForgotPasswordRequestHash requestHash,
            ForgotPasswordIdempotencyReplaySnapshot replaySnapshot,
            Instant now,
            Duration ttl) {
        return idempotency.markCompleted(new ForgotPasswordIdempotencyCompleteCommand(
                ACTION,
                idempotencyKey,
                requestHash,
                replaySnapshot,
                now,
                ttl));
    }

    private ForgotPasswordIdempotencyReplaySnapshot replaySnapshot(String outcomeCode) {
        return new ForgotPasswordIdempotencyReplaySnapshot(outcomeCode, outcomeCode.toLowerCase(), Map.of());
    }

    private ForgotPasswordRequestHash requestHash() {
        try {
            return new ForgotPasswordRequestHash(java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            "FORGOT_PASSWORD\u0000COMPLETE\u0000v1".getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Forgot password completion hash unavailable", exception);
        }
    }

    private CompleteForgotPasswordResult success() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.SUCCESS,
                WorkflowNextStep.RETURN_TO_LOGIN,
                true,
                true,
                List.of());
    }

    private CompleteForgotPasswordResult passwordInvalidResult() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.BUSINESS_REJECTED,
                WorkflowNextStep.PASSWORD_SETUP,
                false,
                false,
                List.of(error(WorkflowErrorType.DOWNSTREAM_BUSINESS, ForgotPasswordCompleteErrorKeys.PASSWORD_INVALID,
                        "password")));
    }

    private CompleteForgotPasswordResult memberNotFoundResult() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.BUSINESS_REJECTED,
                WorkflowNextStep.RETURN_TO_LOGIN,
                true,
                true,
                List.of(error(WorkflowErrorType.DOWNSTREAM_BUSINESS, ForgotPasswordCompleteErrorKeys.MEMBER_NOT_FOUND,
                        "PAGE")));
    }

    private CompleteForgotPasswordResult invalidAuthFlow() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.INVALID_AUTH_FLOW,
                WorkflowNextStep.NONE,
                false,
                false,
                List.of(error(WorkflowErrorType.FORM, ForgotPasswordCompleteErrorKeys.AUTH_FLOW_INVALID, "PAGE")));
    }

    private CompleteForgotPasswordResult csrfInvalid() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.CSRF_INVALID,
                WorkflowNextStep.NONE,
                false,
                false,
                List.of(error(WorkflowErrorType.FORM, ForgotPasswordCompleteErrorKeys.CSRF_INVALID, "PAGE")));
    }

    private CompleteForgotPasswordResult duplicateInProgress() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS,
                WorkflowNextStep.WAIT,
                false,
                false,
                List.of(error(WorkflowErrorType.BUSINESS, SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
                        "PAGE")));
    }

    private CompleteForgotPasswordResult idempotencyKeyReused() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.IDEMPOTENCY_KEY_REUSED,
                WorkflowNextStep.NONE,
                false,
                false,
                List.of(error(WorkflowErrorType.FORM, SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                        "Idempotency-Key")));
    }

    private CompleteForgotPasswordResult unknownOutcome() {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.UNKNOWN_OUTCOME,
                WorkflowNextStep.NONE,
                false,
                false,
                List.of(error(WorkflowErrorType.SYSTEM, ForgotPasswordCompleteErrorKeys.SYSTEM_UNAVAILABLE, "PAGE")));
    }

    private CompleteForgotPasswordResult systemFailed(WorkflowNextStep nextStep) {
        return new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.SYSTEM_FAILED,
                nextStep,
                false,
                false,
                List.of(error(WorkflowErrorType.SYSTEM, ForgotPasswordCompleteErrorKeys.SYSTEM_UNAVAILABLE, "PAGE")));
    }

    private WorkflowErrorDescriptor error(WorkflowErrorType type, String code, String target) {
        return new WorkflowErrorDescriptor(type, code, null, List.of(target), WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER);
    }

    private Mono<Void> releaseAfterSuccess(SessionLockHandle handle) {
        return locks.release(handle).onErrorReturn(false).then();
    }

    private Mono<Void> releaseAfterFailure(SessionLockHandle handle) {
        return locks.release(handle).onErrorResume(error -> Mono.empty()).then();
    }

    private Mono<Void> releaseAfterCancellation(SessionLockHandle handle) {
        return locks.release(handle).onErrorResume(error -> Mono.empty()).then();
    }

    private Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record LoadedContext(ForgotPasswordAuthFlowContext context, CompleteForgotPasswordResult failure) {
        static LoadedContext success(ForgotPasswordAuthFlowContext context) {
            return new LoadedContext(context, null);
        }

        static LoadedContext failure(CompleteForgotPasswordResult failure) {
            return new LoadedContext(null, failure);
        }
    }
}