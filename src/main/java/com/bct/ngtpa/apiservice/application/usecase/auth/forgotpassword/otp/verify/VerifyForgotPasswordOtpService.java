package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class VerifyForgotPasswordOtpService implements VerifyForgotPasswordOtpUseCase {
    private static final String OUTCOME_VERIFIED = "VERIFIED";
    private static final String OUTCOME_INVALID = "OTP_INVALID";
    private static final String OUTCOME_EXPIRED = "OTP_EXPIRED";
    private static final String OUTCOME_LOCKED = "OTP_LOCKED";

    private final ForgotPasswordProtectedAuthFlowContextLoader loader;
    private final ForgotPasswordAuthFlowContextProtectorPort protector;
    private final AuthFlowSessionPort sessions;
    private final AuthFlowSessionMutationLockPort locks;
    private final AuthFlowSessionExpiryRefreshPort expiry;
    private final OtpChallengePort otp;
    private final ForgotPasswordIdempotencyPort idempotency;
    private final Clock clock;
    private final Duration lease;
    private final Duration resetAuthorizationTtl;

    public VerifyForgotPasswordOtpService(
            ForgotPasswordProtectedAuthFlowContextLoader loader,
            ForgotPasswordAuthFlowContextProtectorPort protector,
            AuthFlowSessionPort sessions,
            AuthFlowSessionMutationLockPort locks,
            AuthFlowSessionExpiryRefreshPort expiry,
            OtpChallengePort otp,
            ForgotPasswordIdempotencyPort idempotency,
            Clock clock,
            Duration lease,
            Duration resetAuthorizationTtl) {
        this.loader = Objects.requireNonNull(loader);
        this.protector = Objects.requireNonNull(protector);
        this.sessions = Objects.requireNonNull(sessions);
        this.locks = Objects.requireNonNull(locks);
        this.expiry = Objects.requireNonNull(expiry);
        this.otp = Objects.requireNonNull(otp);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.clock = Objects.requireNonNull(clock);
        this.lease = Objects.requireNonNull(lease);
        this.resetAuthorizationTtl = Objects.requireNonNull(resetAuthorizationTtl);
    }

    @Override
    public Mono<VerifyForgotPasswordOtpResult> verify(VerifyForgotPasswordOtpCommand command) {
        if (command == null || command.sessionId() == null) {
            return Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
        }
        if (!validOtp(command.code())) {
            return Mono.just(result(VerifyForgotPasswordOtpStatus.OTP_INVALID, WorkflowNextStep.VERIFY_OTP));
        }
        return locks.acquire(command.sessionId(), lease)
                .flatMap(handle -> handle.acquired()
                        ? locked(command, handle)
                : Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE)))
            .onErrorReturn(result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE));
    }

    private Mono<VerifyForgotPasswordOtpResult> locked(VerifyForgotPasswordOtpCommand command, SessionLockHandle handle) {
        Mono<VerifyForgotPasswordOtpResult> work = sessions.loadSnapshot(command.sessionId())
                .flatMap(initialOptional -> {
                    if (initialOptional.isEmpty()) {
                        return Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
                    }
                    AuthFlowSession initial = initialOptional.get().session();
                    Instant initialNow = Instant.now(clock);
                    if (initial.isExpired(initialNow)
                            || initial.isTerminal()
                            || initial.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                            || initial.status() != AuthFlowStatus.OTP_SENT) {
                        return Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
                    }
                    return loader.load(new ForgotPasswordProtectedAuthFlowContextCommand(
                            command.sessionId(),
                            command.csrfToken(),
                            command.origin(),
                            command.referer(),
                            command.requestId(),
                            Set.of(AuthFlowStatus.OTP_SENT),
                            true)).flatMap(loaded -> {
                                if (!loaded.isValid()) {
                                    return Mono.just(mapLoaderFailure(loaded.status()));
                                }
                                ForgotPasswordAuthFlowContext context = loaded.context();
                                String resetSubjectId = effectiveResetSubjectId(context);
                                if (context.phase() != ForgotPasswordProtectedPhase.OTP_SENT || blank(loaded.activeOtpChallengeId())
                                        || blank(resetSubjectId)) {
                                    return Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
                                }
                                Instant now = Instant.now(clock);
                                return sessions.loadSnapshot(command.sessionId())
                                        .flatMap(currentOptional -> {
                                            if (currentOptional.isEmpty()) {
                                                return Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
                                            }
                                            AuthFlowSession current = currentOptional.get().session();
                                            if (current.isExpired(now)
                                                    || current.isTerminal()
                                                    || current.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                                                    || current.status() != AuthFlowStatus.OTP_SENT
                                                    || blank(current.activeOtpChallengeId())) {
                                                return Mono.just(result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE));
                                            }
                                            return processIdempotent(
                                                    command,
                                                    current,
                                                    current.activeOtpChallengeId(),
                                                    normalizeResetSubjectId(context, resetSubjectId),
                                                    now);
                                        });
                            });
                })
                .onErrorReturn(result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE));

        return Mono.usingWhen(
                Mono.just(handle),
                ignored -> work,
                ignored -> locks.release(handle).onErrorReturn(false).then(),
                (ignored, error) -> locks.release(handle).onErrorReturn(false).then(),
                ignored -> locks.release(handle).onErrorReturn(false).then());
    }

    private Mono<VerifyForgotPasswordOtpResult> processIdempotent(
            VerifyForgotPasswordOtpCommand command,
            AuthFlowSession session,
            String activeOtpChallengeId,
            ForgotPasswordAuthFlowContext context,
            Instant now) {
        ForgotPasswordIdempotencyKey key = new ForgotPasswordIdempotencyKey(command.idempotencyKey().trim());
        ForgotPasswordRequestHash requestHash = new ForgotPasswordRequestHash(
            sha256("FORGOT_PASSWORD\u0000OTP_VERIFY\u0000v1"));

        return idempotency.startProcessing(new ForgotPasswordIdempotencyStartCommand(
                ForgotPasswordIdempotencyAction.OTP_VERIFY,
                key,
                requestHash,
                now,
                now.plus(command.idempotencyTtl()),
                command.idempotencyTtl())).flatMap(start -> switch (start.outcome()) {
                    case STARTED -> verifyActiveChallenge(command, session, activeOtpChallengeId, context, key, requestHash, now);
                    case COMPLETED_REPLAY -> Mono.just(fromReplay(start.record() == null ? null : start.record().replaySnapshot()));
                    case DUPLICATE_IN_PROGRESS -> Mono.just(result(
                            VerifyForgotPasswordOtpStatus.DUPLICATE_IN_PROGRESS,
                            WorkflowNextStep.WAIT));
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(result(
                            VerifyForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED,
                            WorkflowNextStep.NONE));
                    case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING -> Mono.just(result(
                            VerifyForgotPasswordOtpStatus.SYSTEM_FAILED,
                            WorkflowNextStep.NONE));
                })
                .onErrorReturn(result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE));
    }

    private Mono<VerifyForgotPasswordOtpResult> verifyActiveChallenge(
            VerifyForgotPasswordOtpCommand command,
            AuthFlowSession session,
            String activeOtpChallengeId,
            ForgotPasswordAuthFlowContext context,
            ForgotPasswordIdempotencyKey key,
            ForgotPasswordRequestHash requestHash,
            Instant now) {
        return otp.verifyChallenge(new VerifyOtpChallengeCommand(activeOtpChallengeId, command.code(), now))
                .flatMap(result -> switch (result.outcome()) {
                    case VALID -> authorize(command, session, context, key, requestHash, now);
                    case EXPIRED -> completeReplay(
                            key,
                            requestHash,
                            now,
                            command.idempotencyTtl(),
                            OUTCOME_EXPIRED,
                            VerifyForgotPasswordOtpStatus.OTP_EXPIRED,
                            WorkflowNextStep.RESEND_OTP,
                            result.remainingAttempts());
                    case LOCKED -> completeReplay(
                            key,
                            requestHash,
                            now,
                            command.idempotencyTtl(),
                            OUTCOME_LOCKED,
                            VerifyForgotPasswordOtpStatus.OTP_LOCKED,
                            WorkflowNextStep.RESTART_FLOW,
                            0);
                    case INVALID, INVALIDATED, ALREADY_VERIFIED, NOT_FOUND -> completeReplay(
                            key,
                            requestHash,
                            now,
                            command.idempotencyTtl(),
                            OUTCOME_INVALID,
                            VerifyForgotPasswordOtpStatus.OTP_INVALID,
                            WorkflowNextStep.VERIFY_OTP,
                            result.remainingAttempts());
                });
    }

    private Mono<VerifyForgotPasswordOtpResult> authorize(
            VerifyForgotPasswordOtpCommand command,
            AuthFlowSession reloaded,
            ForgotPasswordAuthFlowContext context,
            ForgotPasswordIdempotencyKey key,
            ForgotPasswordRequestHash requestHash,
            Instant now) {
        String resetSubjectId = effectiveResetSubjectId(context);
        ForgotPasswordResetAuthorization authorization = new ForgotPasswordResetAuthorization(
                opaqueRef("fpra"),
                reloaded.authFlowSessionId().value(),
                resetSubjectId,
                now,
                now.plus(resetAuthorizationTtl),
                null);
        ForgotPasswordAuthFlowContext updatedContext = normalizeResetSubjectId(context, resetSubjectId)
                .withResetAuthorization(authorization)
                .withPhase(ForgotPasswordProtectedPhase.PASSWORD_SETUP);
        ForgotPasswordAuthFlowEncryptedContext encrypted = protector.protect(updatedContext);
        Map<String, String> attrs = new LinkedHashMap<>(reloaded.data().attributes());
        attrs.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
        attrs.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
        attrs.put(CONTEXT_ALGORITHM, encrypted.algorithm());

        AuthFlowSession updated = new AuthFlowSession(
                reloaded.authFlowSessionId(),
                reloaded.scenario(),
                AuthFlowStatus.RESET_AUTHORIZED,
                reloaded.csrfTokenHash(),
                null,
                new AuthFlowSessionData(updatedContext.contactChannel(), updatedContext.maskedDestination(), attrs),
                reloaded.createdAt(),
                now,
                reloaded.expiresAt());

        return new RevisionAwareAuthFlowSessionWriter(sessions)
                .updateExpected(reloaded, updated)
                .then(expiry.refresh(command.sessionId(), now))
                .then(idempotency.markCompleted(new ForgotPasswordIdempotencyCompleteCommand(
                        ForgotPasswordIdempotencyAction.OTP_VERIFY,
                        key,
                        requestHash,
                        replaySnapshot(OUTCOME_VERIFIED, authorization.authorizationId()),
                        now,
                        command.idempotencyTtl())))
                .thenReturn(result(VerifyForgotPasswordOtpStatus.VERIFIED, WorkflowNextStep.PASSWORD_SETUP))
                .onErrorReturn(result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE));
    }

    private Mono<VerifyForgotPasswordOtpResult> completeReplay(
            ForgotPasswordIdempotencyKey key,
            ForgotPasswordRequestHash requestHash,
            Instant now,
            Duration ttl,
            String outcomeCode,
            VerifyForgotPasswordOtpStatus status,
            WorkflowNextStep nextStep,
            Integer remainingVerifyAttempts) {
        return idempotency.markCompleted(new ForgotPasswordIdempotencyCompleteCommand(
                ForgotPasswordIdempotencyAction.OTP_VERIFY,
                key,
                requestHash,
                replaySnapshot(outcomeCode, opaqueRef("fpvr")),
                now,
                ttl)).thenReturn(result(status, nextStep, remainingVerifyAttempts));
    }

    private VerifyForgotPasswordOtpResult fromReplay(ForgotPasswordIdempotencyReplaySnapshot snapshot) {
        if (snapshot == null || snapshot.outcomeCode() == null) {
            return result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE);
        }
        return switch (snapshot.outcomeCode()) {
            case OUTCOME_VERIFIED -> result(VerifyForgotPasswordOtpStatus.COMPLETED_REPLAY, WorkflowNextStep.PASSWORD_SETUP);
            case OUTCOME_INVALID -> result(VerifyForgotPasswordOtpStatus.OTP_INVALID, WorkflowNextStep.VERIFY_OTP);
            case OUTCOME_EXPIRED -> result(VerifyForgotPasswordOtpStatus.OTP_EXPIRED, WorkflowNextStep.RESEND_OTP);
            case OUTCOME_LOCKED -> result(VerifyForgotPasswordOtpStatus.OTP_LOCKED, WorkflowNextStep.RESTART_FLOW);
            default -> result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE);
        };
    }

    private VerifyForgotPasswordOtpResult mapLoaderFailure(ForgotPasswordProtectedAuthFlowContextStatus status) {
        if (status == ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID
                || status == ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID) {
            return result(VerifyForgotPasswordOtpStatus.CSRF_INVALID, WorkflowNextStep.NONE);
        }
        return result(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE);
    }

    private ForgotPasswordIdempotencyReplaySnapshot replaySnapshot(String outcomeCode, String opaqueCommitRef) {
        return new ForgotPasswordIdempotencyReplaySnapshot(outcomeCode, opaqueCommitRef, Map.of());
    }

    private VerifyForgotPasswordOtpResult result(VerifyForgotPasswordOtpStatus status, WorkflowNextStep nextStep) {
        return result(status, nextStep, null);
    }

    private VerifyForgotPasswordOtpResult result(
            VerifyForgotPasswordOtpStatus status,
            WorkflowNextStep nextStep,
            Integer remainingVerifyAttempts) {
        return new VerifyForgotPasswordOtpResult(status, nextStep, remainingVerifyAttempts);
    }

    private boolean validOtp(String code) {
        return code != null && code.matches("\\d{6}");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String effectiveResetSubjectId(ForgotPasswordAuthFlowContext context) {
        if (context == null) {
            return null;
        }
        if (!blank(context.resetSubjectId())) {
            return context.resetSubjectId().trim();
        }
        return blank(context.tasUserId()) ? null : context.tasUserId().trim();
    }

    private ForgotPasswordAuthFlowContext normalizeResetSubjectId(
            ForgotPasswordAuthFlowContext context,
            String resetSubjectId) {
        if (context == null || blank(resetSubjectId) || resetSubjectId.equals(context.resetSubjectId())) {
            return context;
        }
        return new ForgotPasswordAuthFlowContext(
                context.normalizedUsername(),
                context.customerId(),
                context.tasUserId(),
                context.publicIdentityType(),
                context.identityVerified(),
                resetSubjectId,
                context.contactChannel(),
                context.protectedDestination(),
                context.maskedDestination(),
                context.resetAuthorization(),
                context.workflowVersion(),
                context.phase());
    }

    private String opaqueRef(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("OTP verify hash unavailable", exception);
        }
    }
}