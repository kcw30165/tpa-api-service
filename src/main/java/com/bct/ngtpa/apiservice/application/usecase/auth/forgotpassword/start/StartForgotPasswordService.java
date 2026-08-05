package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class StartForgotPasswordService implements StartForgotPasswordUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartForgotPasswordService.class);
    private static final String WORKFLOW_VERSION = "v1";
    private static final String ERROR_USERNAME_NOT_FOUND = "err.forgotpassword.username.notFound";
    private static final String ERROR_SYSTEM_UNAVAILABLE = "err.forgotpassword.system.unavailable";

    private final ForgotPasswordIdempotencyPort idempotencyPort;
    private final ForgotPasswordUsernameLookupPort lookupPort;
    private final ForgotPasswordAuthFlowContextProtectorPort contextProtector;
    private final AuthFlowSessionConcurrencyService concurrencyService;
    private final CreateAuthFlowSessionCsrfUseCase csrfUseCase;
    private final Clock clock;
    private final SessionTtlPolicy sessionTtlPolicy;
    private final SessionConcurrencyPolicy concurrencyPolicy;
    private final String sessionIdPrefix;
    private final int sessionIdEntropyBytes;
    private final boolean trimUsername;
    private final boolean caseInsensitiveUsername;
    private final boolean asciiLowercase;
    private final Map<String, RegistrationIdentityType> tasIdentityTypeMapping;

    public StartForgotPasswordService(
            ForgotPasswordIdempotencyPort idempotencyPort,
            ForgotPasswordUsernameLookupPort lookupPort,
            ForgotPasswordAuthFlowContextProtectorPort contextProtector,
            AuthFlowSessionConcurrencyService concurrencyService,
            CreateAuthFlowSessionCsrfUseCase csrfUseCase,
            Clock clock,
            SessionTtlPolicy sessionTtlPolicy,
            SessionConcurrencyPolicy concurrencyPolicy,
            String sessionIdPrefix,
            int sessionIdEntropyBytes,
            boolean trimUsername,
            boolean caseInsensitiveUsername,
            boolean asciiLowercase,
            Map<String, RegistrationIdentityType> tasIdentityTypeMapping) {
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort, "idempotencyPort must not be null");
        this.lookupPort = Objects.requireNonNull(lookupPort, "lookupPort must not be null");
        this.contextProtector = Objects.requireNonNull(contextProtector, "contextProtector must not be null");
        this.concurrencyService = Objects.requireNonNull(concurrencyService, "concurrencyService must not be null");
        this.csrfUseCase = Objects.requireNonNull(csrfUseCase, "csrfUseCase must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sessionTtlPolicy = Objects.requireNonNull(sessionTtlPolicy, "sessionTtlPolicy must not be null");
        this.concurrencyPolicy = Objects.requireNonNull(concurrencyPolicy, "concurrencyPolicy must not be null");
        this.sessionIdPrefix = Objects.requireNonNull(sessionIdPrefix, "sessionIdPrefix must not be null");
        this.sessionIdEntropyBytes = sessionIdEntropyBytes;
        this.trimUsername = trimUsername;
        this.caseInsensitiveUsername = caseInsensitiveUsername;
        this.asciiLowercase = asciiLowercase;
        this.tasIdentityTypeMapping = Map.copyOf(Objects.requireNonNull(
                tasIdentityTypeMapping,
                "tasIdentityTypeMapping must not be null"));
    }

    public Mono<StartForgotPasswordResult> start(StartForgotPasswordCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String normalizedUsername = normalizeUsername(command.username());
        Instant now = Instant.now(clock);
        ForgotPasswordRequestHash requestHash = hash(normalizedUsername);
        return idempotencyPort.startProcessing(new ForgotPasswordIdempotencyStartCommand(
                ForgotPasswordIdempotencyAction.START,
                command.idempotencyKey(),
                requestHash,
                now,
                now.plus(command.idempotencyTtl()),
                command.idempotencyTtl()))
                .flatMap(start -> switch (start.outcome()) {
                    case STARTED -> startFresh(command, normalizedUsername, requestHash, now);
                    case COMPLETED_REPLAY -> replay(command, requestHash, start);
                    case DUPLICATE_IN_PROGRESS ->
                        Mono.just(simple(StartForgotPasswordResultStatus.DUPLICATE_IN_PROGRESS));
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                        Mono.just(simple(StartForgotPasswordResultStatus.IDEMPOTENCY_KEY_REUSED));
                    case FAILED_EXISTING -> Mono.just(simple(StartForgotPasswordResultStatus.FAILED_EXISTING));
                    case UNKNOWN_OUTCOME_EXISTING -> Mono.just(simple(StartForgotPasswordResultStatus.UNKNOWN_OUTCOME));
                });
    }

    private Mono<StartForgotPasswordResult> startFresh(
            StartForgotPasswordCommand command,
            String normalizedUsername,
            ForgotPasswordRequestHash requestHash,
            Instant now) {
        return lookupPort
                .lookup(new StartForgotPasswordUsernameLookupCommand(normalizedUsername, command.correlationId()))
                .flatMap(result -> switch (result.status()) {
                    case CONFIRMED -> createStartedResult(command, normalizedUsername, requestHash, now, result);
                    case NOT_FOUND -> fail(command, requestHash).thenReturn(notFound());
                    case SYSTEM_FAILED -> fail(command, requestHash).thenReturn(systemFailed());
                })
                .onErrorResume(error -> {
                    LOGGER.error(
                            "event=forgot-password.start.failed requestId={} stage=username-lookup exceptionType={}",
                            command.correlationId(), error.getClass().getName(), error);
                    return fail(command, requestHash).thenReturn(systemFailed());
                });
    }

    private Mono<StartForgotPasswordResult> createStartedResult(
            StartForgotPasswordCommand command,
            String normalizedUsername,
            ForgotPasswordRequestHash requestHash,
            Instant now,
            ForgotPasswordUsernameLookupResult lookupResult) {
        String publicIdentityType = publicIdentityType(lookupResult.identityType());
        var protectedContext = contextProtector.protect(new ForgotPasswordAuthFlowContext(
                normalizedUsername,
                lookupResult.customerId(),
                lookupResult.userId(),
                publicIdentityType,
                false,
                null,
                null,
                null,
                null,
                null,
                WORKFLOW_VERSION));
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT, protectedContext.ciphertext());
        attributes.put(ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION, protectedContext.keyVersion());
        attributes.put(ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM, protectedContext.algorithm());
        var sessionData = new AuthFlowSessionData(null, null, attributes);
        var sessionCommand = new StartAuthFlowSessionWithConcurrencyCommand(
                AuthFlowScenario.FORGOT_PASSWORD,
                AuthFlowStatus.STARTED,
                sessionTtlPolicy,
                sessionIdPrefix,
                sessionIdEntropyBytes,
                sessionData,
                null,
                null,
                authFlowSessionId(command.existingAuthFlowSessionId()),
                concurrencyPolicy,
                command.correlationId());
        return concurrencyService.start(sessionCommand)
                .flatMap(started -> csrfUseCase.createForSession(new CreateAuthFlowSessionCsrfCommand(
                        started.session().authFlowSessionId(),
                        now,
                        started.expiresInSeconds(),
                        command.correlationId()))
                .flatMap(csrf -> idempotencyPort.markCompleted(new ForgotPasswordIdempotencyCompleteCommand(
                                ForgotPasswordIdempotencyAction.START,
                                command.idempotencyKey(),
                                requestHash,
                                new ForgotPasswordIdempotencyReplaySnapshot(
                                        StartForgotPasswordResultStatus.STARTED.name(),
                                        started.session().authFlowSessionId().value(),
                                        Map.of(),
                                        publicIdentityType),
                                Instant.now(clock),
                                command.idempotencyTtl()))
                                .thenReturn(success(
                                        StartForgotPasswordResultStatus.STARTED,
                                        publicIdentityType,
                                        started.session().authFlowSessionId().value(),
                                        csrf.rawToken(),
                                        null))))
                .onErrorResume(error -> {
                    LOGGER.error(
                            "event=forgot-password.start.failed requestId={} stage=session-replacement-or-csrf exceptionType={}",
                            command.correlationId(), error.getClass().getName(), error);
                    if (isSessionReplacementConflict(error)) {
                        return fail(command, requestHash).thenReturn(authFlowInvalid());
                    }
                    return fail(command, requestHash).thenReturn(systemFailed());
                });
    }

    private Mono<StartForgotPasswordResult> replay(
            StartForgotPasswordCommand command,
            ForgotPasswordRequestHash requestHash,
            com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult start) {
        var snapshot = start.record() == null ? null : start.record().replaySnapshot();
        if (snapshot == null || snapshot.opaqueCommitRef() == null || snapshot.opaqueCommitRef().isBlank()) {
            return fail(command, requestHash).thenReturn(systemFailed());
        }
        AuthFlowSessionId sessionId = new AuthFlowSessionId(snapshot.opaqueCommitRef());
        return csrfUseCase.createForSession(new CreateAuthFlowSessionCsrfCommand(
                sessionId,
                Instant.now(clock),
                sessionTtlPolicy.ttlSeconds(),
                command.correlationId()))
                .map(csrf -> success(
                        StartForgotPasswordResultStatus.COMPLETED_REPLAY,
                        snapshot == null ? null : snapshot.identityType(),
                        sessionId.value(),
                        csrf.rawToken(),
                        snapshot))
                .onErrorResume(error -> {
                    LOGGER.error(
                            "event=forgot-password.start.failed requestId={} stage=session-replacement-or-csrf exceptionType={}",
                            command.correlationId(), error.getClass().getName(), error);
                    return fail(command, requestHash).thenReturn(systemFailed());
                });
    }

    private Mono<Void> fail(StartForgotPasswordCommand command, ForgotPasswordRequestHash requestHash) {
        return idempotencyPort.markFailed(new ForgotPasswordIdempotencyFailCommand(
                ForgotPasswordIdempotencyAction.START,
                command.idempotencyKey(),
                requestHash,
                Instant.now(clock),
                command.idempotencyTtl()))
                .then();
    }

    private StartForgotPasswordResult success(
            StartForgotPasswordResultStatus status,
            String identityType,
            String authFlowSessionId,
            String csrfToken,
            ForgotPasswordIdempotencyReplaySnapshot replaySnapshot) {
        return new StartForgotPasswordResult(
                status,
                WorkflowNextStep.ENTER_ID,
                csrfToken,
                identityType,
                authFlowSessionId,
                List.of(),
                replaySnapshot);
    }

    private StartForgotPasswordResult notFound() {
        return new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
                WorkflowNextStep.ENTER_USERNAME,
                null,
                null,
                null,
                List.of(new StartForgotPasswordError(ERROR_USERNAME_NOT_FOUND, "username", null)),
                null);
    }

    private StartForgotPasswordResult authFlowInvalid() {
        return new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.AUTH_FLOW_INVALID,
                WorkflowNextStep.RESTART_FLOW,
                null,
                null,
                null,
                List.of(new StartForgotPasswordError(ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value(), "PAGE", null)),
                null);
    }

    private StartForgotPasswordResult systemFailed() {
        return new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.SYSTEM_FAILED,
                WorkflowNextStep.NONE,
                null,
                null,
                null,
                List.of(new StartForgotPasswordError(ERROR_SYSTEM_UNAVAILABLE, "PAGE", null)),
                null);
    }

    private StartForgotPasswordResult simple(StartForgotPasswordResultStatus status) {
        return new StartForgotPasswordResult(status, WorkflowNextStep.NONE, null, null, null, List.of(), null);
    }

    private boolean isSessionReplacementConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && "Auth flow session revision conflict".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AuthFlowSessionId authFlowSessionId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new AuthFlowSessionId(value);
    }

    private String normalizeUsername(String username) {
        String normalized = username;
        if (trimUsername) {
            normalized = normalized.trim();
        }
        if (caseInsensitiveUsername) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        if (asciiLowercase) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private ForgotPasswordRequestHash hash(String normalizedUsername) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ForgotPasswordIdempotencyAction.START.name().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(normalizedUsername.getBytes(StandardCharsets.UTF_8));
            return new ForgotPasswordRequestHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Forgot password request hash algorithm unavailable", exception);
        }
    }

    private String publicIdentityType(RegistrationIdentityType identityType) {
        return identityType == RegistrationIdentityType.STAFFNO ? "STAFF_NO" : identityType.name();
    }
}