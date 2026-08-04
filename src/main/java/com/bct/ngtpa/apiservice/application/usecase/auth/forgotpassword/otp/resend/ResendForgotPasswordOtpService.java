package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.*;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class ResendForgotPasswordOtpService implements ResendForgotPasswordOtpUseCase {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(ResendForgotPasswordOtpService.class);
    private static final String EXPIRES_AT = "forgotPasswordOtpExpiresAt";
    private static final String RESEND_AT = "forgotPasswordOtpResendAvailableAt";
    private static final String RESEND_COUNT = "forgotPasswordOtpSuccessfulResendCount";
    private static final String PENDING_REF = "forgotPasswordOtpPendingResendRef";
    private static final String PENDING_CANDIDATE = "forgotPasswordOtpPendingCandidateId";
    private static final String PENDING_PREVIOUS = "forgotPasswordOtpPendingPreviousChallengeId";

    private final ForgotPasswordIdempotencyPort idempotency;
    private final SecureForgotPasswordOtpCommitRefGenerator refs;
    private final AuthFlowSessionMutationLockPort locks;
    private final AuthFlowSessionPort sessions;
    private final ForgotPasswordAuthFlowContextProtectorPort protector;
    private final EvaluateForgotPasswordOtpResendUseCase foundation;
    private final OtpChallengePort otp;
    private final OtpChallengeStatePort challengeState;
    private final MessageDeliveryPort delivery;
    private final ForgotPasswordDestinationMasker masker;
    private final ForgotPasswordOtpCommitCoordinator commits;
    private final ForgotPasswordOtpReplayResolver replay;
    private final ForgotPasswordOtpTemplates templates;
    private final ForgotPasswordInitialOtpPolicy otpPolicy;
    private final ForgotPasswordOtpResendRequestHashGenerator hashes;
    private final CsrfTokenValidationService csrfTokens;
    private final OriginRefererValidator origins;
    private final List<String> allowedOrigins;
    private final Clock clock;
    private final Duration idempotencyTtl;
    private final Duration lockLease;

    public ResendForgotPasswordOtpService(
            ForgotPasswordIdempotencyPort idempotency,
            SecureForgotPasswordOtpCommitRefGenerator refs,
            AuthFlowSessionMutationLockPort locks,
            AuthFlowSessionPort sessions,
            ForgotPasswordAuthFlowContextProtectorPort protector,
            EvaluateForgotPasswordOtpResendUseCase foundation,
            OtpChallengePort otp,
            OtpChallengeStatePort challengeState,
            MessageDeliveryPort delivery,
            ForgotPasswordDestinationMasker masker,
            ForgotPasswordOtpCommitCoordinator commits,
            ForgotPasswordOtpReplayResolver replay,
            ForgotPasswordOtpTemplates templates,
            ForgotPasswordInitialOtpPolicy otpPolicy,
            ForgotPasswordOtpResendRequestHashGenerator hashes,
            Clock clock,
            Duration idempotencyTtl,
            Duration lockLease,
            List<String> allowedOrigins) {
        this.idempotency = Objects.requireNonNull(idempotency);
        this.refs = Objects.requireNonNull(refs);
        this.locks = Objects.requireNonNull(locks);
        this.sessions = Objects.requireNonNull(sessions);
        this.protector = Objects.requireNonNull(protector);
        this.foundation = Objects.requireNonNull(foundation);
        this.otp = Objects.requireNonNull(otp);
        this.challengeState = Objects.requireNonNull(challengeState);
        this.delivery = Objects.requireNonNull(delivery);
        this.masker = Objects.requireNonNull(masker);
        this.commits = Objects.requireNonNull(commits);
        this.replay = Objects.requireNonNull(replay);
        this.templates = Objects.requireNonNull(templates);
        this.otpPolicy = Objects.requireNonNull(otpPolicy);
        this.hashes = Objects.requireNonNull(hashes);
        this.csrfTokens = new CsrfTokenValidationService(new Sha256CsrfTokenHasher());
        this.origins = new OriginRefererValidator();
        this.clock = Objects.requireNonNull(clock);
        this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl);
        this.lockLease = Objects.requireNonNull(lockLease);
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    @Override
    public Mono<ResendForgotPasswordOtpResult> resend(ResendForgotPasswordOtpCommand command) {
        Instant now = Instant.now(clock);
        ForgotPasswordIdempotencyKey key = new ForgotPasswordIdempotencyKey(command.idempotencyKey().trim());
        ForgotPasswordRequestHash hash = hashes.generate();
        return idempotency.startProcessing(new ForgotPasswordIdempotencyStartCommand(
                ForgotPasswordIdempotencyAction.OTP_RESEND,
                key,
                hash,
                now,
                now.plus(idempotencyTtl),
                idempotencyTtl)).flatMap(start -> switch (start.outcome()) {
                    case STARTED -> startNew(command, start.record(), hash, now);
                    case COMPLETED_REPLAY -> resolveCommittedReplay(start.record());
                    case DUPLICATE_IN_PROGRESS -> resolveInProgress(command.sessionId(), start.record());
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(new ResendForgotPasswordOtpResult(
                            ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED, null, null, null));
                    case FAILED_EXISTING -> Mono.just(new ResendForgotPasswordOtpResult(
                            ResendForgotPasswordOtpStatus.DELIVERY_FAILED, null, null, null));
                    case UNKNOWN_OUTCOME_EXISTING -> Mono.just(new ResendForgotPasswordOtpResult(
                            ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME, null, null, null));
                }).onErrorReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null,
                        null, null));
    }

    private Mono<ResendForgotPasswordOtpResult> resolveCommittedReplay(ForgotPasswordIdempotencyRecord record) {
        if (record == null || record.replaySnapshot() == null) {
            return Mono.just(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null,
                    null));
        }
        return replay.resolve(record.replaySnapshot().opaqueCommitRef()).map(this::fromReplay)
                .onErrorReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null,
                        null, null));
    }

    private Mono<ResendForgotPasswordOtpResult> resolveInProgress(AuthFlowSessionId sessionId,
            ForgotPasswordIdempotencyRecord record) {
        if (record == null || record.pendingOpaqueCommitRef() == null || record.pendingOpaqueCommitRef().isBlank()) {
            return Mono.just(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null,
                    null));
        }
        return replay.resolve(record.pendingOpaqueCommitRef()).flatMap(resolved -> {
            ResendForgotPasswordOtpResult replayResult = fromReplay(resolved);
            if (replayResult.status() != ResendForgotPasswordOtpStatus.SYSTEM_FAILED) {
                return Mono.just(replayResult);
            }
            return resolvePendingSessionReplay(sessionId, record.pendingOpaqueCommitRef());
        }).onErrorReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null,
                null));
    }

    private Mono<ResendForgotPasswordOtpResult> resolvePendingSessionReplay(AuthFlowSessionId sessionId,
            String pendingRef) {
        return sessions.loadSnapshot(sessionId).flatMap(optional -> optional.map(Mono::just)
                .orElseGet(() -> Mono.just(new AuthFlowSessionSnapshot(invalidSession(sessionId), new SessionRevision(
                        "00000000000000000000000000000000"))))
                .flatMap(snapshot -> {
                    AuthFlowSession session = snapshot.session();
                    if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD || session.status() != AuthFlowStatus.OTP_SENT
                            || session.isTerminal() || session.isExpired(Instant.now(clock))) {
                        return Mono.just(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED,
                                null, null, null));
                    }
                    ForgotPasswordAuthFlowContext context = decrypt(session);
                    if (context.phase() == ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN) {
                        return Mono.just(new ResendForgotPasswordOtpResult(
                                ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME, null, null, null));
                    }
                    String sessionPendingRef = session.data().attributes().get(PENDING_REF);
                    String candidateId = session.data().attributes().get(PENDING_CANDIDATE);
                    if (!Objects.equals(pendingRef, sessionPendingRef)
                            || candidateId == null
                            || !candidateId.equals(session.activeOtpChallengeId())) {
                        return Mono.just(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED,
                                null, null, null));
                    }
                    return challengeState.findById(candidateId).map(optionalChallenge -> optionalChallenge
                            .map(challenge -> liveResult(session, challenge, context.maskedDestination(), Instant.now(clock)))
                            .orElseGet(() -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED,
                                    null, null, null)));
                })).onErrorReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null,
                        null, null));
    }

    private Mono<ResendForgotPasswordOtpResult> startNew(ResendForgotPasswordOtpCommand command,
            ForgotPasswordIdempotencyRecord record, ForgotPasswordRequestHash hash, Instant now) {
        String pendingRef = refs.generate();
        return idempotency.attachPendingCommitRef(new ForgotPasswordPendingCommitRefCommand(
                ForgotPasswordIdempotencyAction.OTP_RESEND,
                record.idempotencyKey(),
                hash,
                pendingRef,
                idempotencyTtl)).flatMap(attached -> {
                    String effectiveRef = attached.opaqueCommitRef();
                    return locks.acquire(command.sessionId(), lockLease)
                            .flatMap(handle -> handle.acquired()
                                    ? locked(command, record, hash, effectiveRef, handle)
                                    : Mono.just(new ResendForgotPasswordOtpResult(
                                            ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null)));
                });
    }

    private Mono<ResendForgotPasswordOtpResult> locked(ResendForgotPasswordOtpCommand command,
            ForgotPasswordIdempotencyRecord record,
            ForgotPasswordRequestHash hash,
            String pendingRef,
            SessionLockHandle handle) {
        Mono<ResendForgotPasswordOtpResult> work = sessions.loadSnapshot(command.sessionId()).flatMap(optional -> {
            if (optional.isEmpty()) {
                return Mono.just(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID,
                        null, null, null));
            }
            AuthFlowSessionSnapshot snapshot = optional.get();
            return validateEligibility(command, handle, snapshot).flatMap(validated -> {
                if (validated.status() != ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE) {
                    return Mono.just(mapEligibility(validated, validated.maskedDestination(), validated.expiresInSeconds()));
                }
                return createCandidateAndDeliver(command, record, hash, pendingRef, snapshot, validated);
            });
        }).onErrorReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null,
                null));
        return Mono.usingWhen(
                Mono.just(handle),
                ignored -> work,
                ignored -> locks.release(handle).onErrorReturn(false).then(),
                (ignored, error) -> locks.release(handle).onErrorReturn(false).then(),
                ignored -> locks.release(handle).onErrorReturn(false).then());
    }

    private Mono<ValidatedState> validateEligibility(ResendForgotPasswordOtpCommand command, SessionLockHandle handle,
            AuthFlowSessionSnapshot snapshot) {
        AuthFlowSession session = snapshot.session();
        Instant now = Instant.now(clock);
        if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD || session.status() != AuthFlowStatus.OTP_SENT
                || session.isTerminal() || session.isExpired(now)) {
            return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID));
        }
        if (!csrfTokens.validate(command.csrfToken(), session.csrfTokenHash()).valid()) {
            return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID, true));
        }
                if (!origins.validate(command.origin(), command.referer(), allowedOrigins).valid()) {
            return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID, true));
        }
        ForgotPasswordAuthFlowContext context;
        try {
            context = decrypt(session);
        } catch (RuntimeException ex) {
            return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID));
        }
        if (blank(context.contactChannel()) || blank(context.protectedDestination()) || blank(context.maskedDestination())
                || !Objects.equals(masker.mask(context.contactChannel(), context.protectedDestination()),
                        context.maskedDestination())
                || !Objects.equals(session.data().maskedDestination(), context.maskedDestination())) {
            return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.CHALLENGE_BINDING_INVALID));
        }
        if (blank(session.activeOtpChallengeId())) {
            return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.NO_ACTIVE_CHALLENGE,
                    false, context.maskedDestination(), null));
        }
        return challengeState.findById(session.activeOtpChallengeId()).flatMap(optionalChallenge -> {
            if (optionalChallenge.isEmpty()) {
                return Mono.just(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.NO_ACTIVE_CHALLENGE,
                        false, context.maskedDestination(), null));
            }
            OtpChallenge authoritative = optionalChallenge.get();
            if (authoritative.scenario() != OtpScenario.FORGOT_PASSWORD
                    || !Objects.equals(authoritative.workflowRef(), session.authFlowSessionId().value())
                    || !Objects.equals(authoritative.destinationHash(), sha256(context.protectedDestination()))) {
                return Mono.just(ValidatedState.invalid(
                        ForgotPasswordOtpResendEligibilityStatus.CHALLENGE_BINDING_INVALID,
                        false,
                        context.maskedDestination(),
                        secondsUntil(now, authoritative.expiresAt())));
            }
            int successfulResendCount = readCount(session.data().attributes());
            return foundation.evaluate(new ForgotPasswordOtpResendCommand(
                    handle,
                    session,
                    authoritative,
                    context.phase(),
                    successfulResendCount,
                    command.idempotencyKey(),
                    now)).map(foundationResult -> new ValidatedState(
                            foundationResult.status(),
                            context,
                            authoritative,
                            successfulResendCount,
                            foundationResult.retryAfterSeconds(),
                            context.maskedDestination(),
                            secondsUntil(now, authoritative.expiresAt()),
                            false)).onErrorReturn(new ValidatedState(
                                    ForgotPasswordOtpResendEligibilityStatus.SYSTEM_FAILED,
                                    context,
                                    authoritative,
                                    successfulResendCount,
                                    null,
                                    context.maskedDestination(),
                                    secondsUntil(now, authoritative.expiresAt()),
                                    false));
        }).onErrorReturn(ValidatedState.invalid(ForgotPasswordOtpResendEligibilityStatus.SYSTEM_FAILED));
    }

    private Mono<ResendForgotPasswordOtpResult> createCandidateAndDeliver(ResendForgotPasswordOtpCommand command,
            ForgotPasswordIdempotencyRecord record,
            ForgotPasswordRequestHash hash,
            String pendingRef,
            AuthFlowSessionSnapshot snapshot,
            ValidatedState validated) {
        Instant now = Instant.now(clock);
        OtpChannel channel = "email".equalsIgnoreCase(validated.context.contactChannel()) ? OtpChannel.EMAIL
                : OtpChannel.SMS;
        return otp.createChallenge(new CreateOtpChallengeCommand(
                OtpScenario.FORGOT_PASSWORD,
                snapshot.session().authFlowSessionId().value(),
                channel,
                sha256(validated.context.protectedDestination()),
                otpPolicy.length(),
                otpPolicy.numericOnly(),
                otpPolicy.ttlSeconds(),
                otpPolicy.maxVerifyAttempts(),
                otpPolicy.maxResendAttempts(),
                now)).flatMap(candidate -> stagePending(snapshot, validated.context, validated.authoritativeChallenge,
                        candidate.otpChallengeId(), pendingRef, now)
                                .flatMap(stagedSnapshot -> delivery.deliver(message(command, validated.context,
                                        candidate.rawCode()))
                                        .flatMap(deliveryResult -> transition(command, record, hash, stagedSnapshot,
                                                validated, candidate, pendingRef, deliveryResult, now))));
    }

    private Mono<AuthFlowSessionSnapshot> stagePending(AuthFlowSessionSnapshot snapshot,
            ForgotPasswordAuthFlowContext context,
            OtpChallenge authoritative,
            String candidateId,
            String pendingRef,
            Instant now) {
        Map<String, String> attrs = new LinkedHashMap<>(snapshot.session().data().attributes());
        attrs.put(PENDING_REF, pendingRef);
        attrs.put(PENDING_CANDIDATE, candidateId);
        attrs.put(PENDING_PREVIOUS, authoritative.otpChallengeId());
        return mutateSession(snapshot, snapshot.session().activeOtpChallengeId(), context, attrs, now);
    }

    private Mono<ResendForgotPasswordOtpResult> transition(ResendForgotPasswordOtpCommand command,
            ForgotPasswordIdempotencyRecord record,
            ForgotPasswordRequestHash hash,
            AuthFlowSessionSnapshot stagedSnapshot,
            ValidatedState validated,
            CreateOtpChallengeResult candidate,
            String pendingRef,
            MessageDeliveryResult deliveryResult,
            Instant now) {
        MessageDeliveryStatus status = deliveryResult.status();
        if (status == MessageDeliveryStatus.ACCEPTED) {
            return accepted(record, stagedSnapshot, validated, candidate, pendingRef, now);
        }
        if (status == MessageDeliveryStatus.FAILED) {
            return failed(record, hash, stagedSnapshot, validated.context, candidate.otpChallengeId(), now);
        }
        return unknown(record, hash, stagedSnapshot, validated.context, candidate.otpChallengeId(), now);
    }

    private Mono<ResendForgotPasswordOtpResult> accepted(ForgotPasswordIdempotencyRecord record,
            AuthFlowSessionSnapshot stagedSnapshot,
            ValidatedState validated,
            CreateOtpChallengeResult candidate,
            String pendingRef,
            Instant now) {
        Map<String, String> attrs = new LinkedHashMap<>(stagedSnapshot.session().data().attributes());
        attrs.put(EXPIRES_AT, candidate.expiresAt().toString());
        attrs.put(RESEND_AT, now.plusSeconds(otpPolicy.resendCooldownSeconds()).toString());
        attrs.put(RESEND_COUNT, Integer.toString(validated.successfulResendCount + 1));
        ForgotPasswordAuthFlowContext context = validated.context.withPhase(ForgotPasswordProtectedPhase.OTP_SENT);
        return mutateSession(stagedSnapshot, candidate.otpChallengeId(), context, attrs, now)
                .flatMap(updatedSnapshot -> invalidateChallenge(validated.authoritativeChallenge, now)
                        .then(commits.complete(withPending(record, pendingRef),
                                new ForgotPasswordOtpCommitState(updatedSnapshot.session().authFlowSessionId().value(),
                                        candidate.otpChallengeId(), now, candidate.expiresAt()),
                                now))
                        .thenReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.RESENT,
                                validated.context.maskedDestination(), candidate.expiresInSeconds(),
                                otpPolicy.resendCooldownSeconds())));
    }

    private Mono<ResendForgotPasswordOtpResult> failed(ForgotPasswordIdempotencyRecord record,
            ForgotPasswordRequestHash hash,
            AuthFlowSessionSnapshot stagedSnapshot,
            ForgotPasswordAuthFlowContext context,
            String candidateId,
            Instant now) {
        return invalidateChallenge(candidateId, now)
                .then(clearPending(stagedSnapshot, context, now))
                .then(idempotency.markFailed(new ForgotPasswordIdempotencyFailCommand(record.action(),
                        record.idempotencyKey(), hash, now, idempotencyTtl)))
                .thenReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.DELIVERY_FAILED,
                        context.maskedDestination(), null, null));
    }

    private Mono<ResendForgotPasswordOtpResult> unknown(ForgotPasswordIdempotencyRecord record,
            ForgotPasswordRequestHash hash,
            AuthFlowSessionSnapshot stagedSnapshot,
            ForgotPasswordAuthFlowContext context,
            String candidateId,
            Instant now) {
        return invalidateChallenge(candidateId, now)
                .then(updatePhase(stagedSnapshot, context.withPhase(ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN),
                        now))
                .then(idempotency.markUnknownOutcome(new ForgotPasswordIdempotencyUnknownOutcomeCommand(
                        record.action(), record.idempotencyKey(), hash, now, idempotencyTtl)))
                .thenReturn(new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME,
                        context.maskedDestination(), null, null));
    }

    private Mono<AuthFlowSessionSnapshot> clearPending(AuthFlowSessionSnapshot snapshot,
            ForgotPasswordAuthFlowContext context,
            Instant now) {
        Map<String, String> attrs = new LinkedHashMap<>(snapshot.session().data().attributes());
        attrs.remove(PENDING_REF);
        attrs.remove(PENDING_CANDIDATE);
        attrs.remove(PENDING_PREVIOUS);
        return mutateSession(snapshot, snapshot.session().activeOtpChallengeId(), context, attrs, now);
    }

    private Mono<AuthFlowSessionSnapshot> updatePhase(AuthFlowSessionSnapshot snapshot,
            ForgotPasswordAuthFlowContext context,
            Instant now) {
        Map<String, String> attrs = new LinkedHashMap<>(snapshot.session().data().attributes());
        attrs.remove(PENDING_REF);
        attrs.remove(PENDING_CANDIDATE);
        attrs.remove(PENDING_PREVIOUS);
        return mutateSession(snapshot, snapshot.session().activeOtpChallengeId(), context, attrs, now);
    }

    private Mono<AuthFlowSessionSnapshot> mutateSession(AuthFlowSessionSnapshot snapshot,
            String activeChallengeId,
            ForgotPasswordAuthFlowContext context,
            Map<String, String> attrs,
            Instant now) {
        ForgotPasswordAuthFlowEncryptedContext encrypted = protector.protect(context);
        attrs.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
        attrs.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
        attrs.put(CONTEXT_ALGORITHM, encrypted.algorithm());
        AuthFlowSession session = snapshot.session();
        AuthFlowSession updated = new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                AuthFlowStatus.OTP_SENT,
                session.csrfTokenHash(),
                activeChallengeId,
                new AuthFlowSessionData(context.contactChannel(), context.maskedDestination(), attrs),
                session.createdAt(),
                now,
                session.expiresAt());
        return sessions.updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(snapshot, updated)).flatMap(result ->
                result.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                        ? Mono.just(result.snapshot())
                        : Mono.error(new IllegalStateException("Auth flow session resend mutation failed: "
                                + result.outcome())));
    }

    private Mono<Void> invalidateChallenge(OtpChallenge challenge, Instant now) {
        OtpChallenge updated = new OtpChallenge(
                challenge.otpChallengeId(),
                challenge.scenario(),
                challenge.workflowRef(),
                challenge.channel(),
                challenge.destinationHash(),
                challenge.codeHash(),
                OtpStatus.INVALIDATED,
                challenge.failedAttemptCount(),
                challenge.maxVerifyAttempts(),
                challenge.resendAttemptCount(),
                challenge.maxResendAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.lastSentAt(),
                now,
                challenge.verifiedAt());
        return challengeState.update(updated);
    }

    private Mono<Void> invalidateChallenge(String challengeId, Instant now) {
        return challengeState.findById(challengeId)
                .flatMap(optional -> optional.map(challenge -> invalidateChallenge(challenge, now))
                        .orElseGet(Mono::empty));
    }

    private MessageDeliveryCommand message(ResendForgotPasswordOtpCommand command,
            ForgotPasswordAuthFlowContext context,
            String rawCode) {
        boolean email = "email".equalsIgnoreCase(context.contactChannel());
        String language = command.language();
        return new MessageDeliveryCommand(
                MessageDeliveryType.FORGOT_PASSWORD_OTP,
                MessageDeliveryScenario.FORGOT_PASSWORD,
                email ? MessageDeliveryChannel.EMAIL : MessageDeliveryChannel.SMS,
                context.protectedDestination(),
                email ? templates.emailSubject(language) : null,
                email ? templates.emailBody(language) : templates.sms(language),
                null,
                Map.of("code", rawCode, "ttlValue", Integer.toString(Math.max(1, otpPolicy.ttlSeconds() / 60)),
                        "ttlUnit", ttlUnit(language)),
                language,
                command.requestId());
    }

    private String ttlUnit(String language) {
        return language != null && language.trim().replace('-', '_').equalsIgnoreCase("zh_HK")
                ? "分鐘"
                : "minutes";
    }

    private ResendForgotPasswordOtpResult mapEligibility(ValidatedState validated, String maskedDestination,
            Integer expiresInSeconds) {
        return switch (validated.status()) {
            case COOLDOWN_ACTIVE -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE,
                    maskedDestination, expiresInSeconds, validated.retryAfterSeconds());
            case RESEND_LIMIT_REACHED -> new ResendForgotPasswordOtpResult(
                    ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED, maskedDestination, expiresInSeconds, 0);
            case SESSION_INVALID, CHALLENGE_BINDING_INVALID, INVALID_PHASE, NO_ACTIVE_CHALLENGE ->
                validated.securityRejected()
                        ? new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SECURITY_REJECTED, null, null,
                                null)
                        : new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID, null, null,
                                null);
            case UNKNOWN_OUTCOME_BLOCKED -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME,
                    maskedDestination, expiresInSeconds, null);
            default -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null,
                    null);
        };
    }

    private ResendForgotPasswordOtpResult fromReplay(ForgotPasswordOtpReplayResult replayResult) {
        return switch (replayResult.status()) {
            case ACTIVE -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.COMPLETED_REPLAY,
                    replayResult.maskedDestination(), replayResult.expiresInSeconds(),
                    replayResult.resendAvailableInSeconds());
            case EXPIRED -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.EXPIRED,
                    replayResult.maskedDestination(), replayResult.expiresInSeconds(),
                    replayResult.resendAvailableInSeconds());
            case SYSTEM_ERROR -> new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null,
                    null, null);
        };
    }

    private ResendForgotPasswordOtpResult liveResult(AuthFlowSession session, OtpChallenge challenge,
            String maskedDestination, Instant now) {
        if (challenge.scenario() != OtpScenario.FORGOT_PASSWORD
                || !Objects.equals(challenge.workflowRef(), session.authFlowSessionId().value())) {
            return new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null);
        }
        if (!challenge.expiresAt().isAfter(now)) {
            return new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.EXPIRED, maskedDestination, 0,
                    0);
        }
        String resendValue = session.data().attributes().get(RESEND_AT);
        if (resendValue == null || resendValue.isBlank()) {
            return new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null);
        }
        try {
            Instant resendAvailableAt = Instant.parse(resendValue);
            return new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.COMPLETED_REPLAY,
                    maskedDestination,
                    secondsUntil(now, challenge.expiresAt()),
                    secondsUntil(now, resendAvailableAt));
        } catch (RuntimeException malformed) {
            return new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null);
        }
    }

    private ForgotPasswordAuthFlowContext decrypt(AuthFlowSession session) {
        Map<String, String> attributes = session.data() == null || session.data().attributes() == null
                ? Map.of()
                : session.data().attributes();
        return protector.unprotect(new ForgotPasswordAuthFlowEncryptedContext(
                attributes.get(CONTEXT_CIPHERTEXT),
                attributes.get(CONTEXT_KEY_VERSION),
                attributes.getOrDefault(CONTEXT_ALGORITHM, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)));
    }

    private int readCount(Map<String, String> attributes) {
        if (attributes == null) {
            return 0;
        }
        String value = attributes.get(RESEND_COUNT);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(0, parsed);
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    private Integer secondsUntil(Instant now, Instant end) {
        if (now == null || end == null) {
            return null;
        }
        long seconds = Math.max(0L, Duration.between(now, end).toSeconds());
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("forgot password resend hash unavailable");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ForgotPasswordIdempotencyRecord withPending(ForgotPasswordIdempotencyRecord record, String ref) {
        return new ForgotPasswordIdempotencyRecord(record.action(), record.idempotencyKey(), record.requestHash(),
                record.status(), record.replaySnapshot(), record.createdAt(), record.updatedAt(), record.expiresAt(),
                ref);
    }

    private AuthFlowSession invalidSession(AuthFlowSessionId sessionId) {
        Instant now = Instant.EPOCH;
        return new AuthFlowSession(sessionId, AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.CANCELLED, null, null,
                new AuthFlowSessionData(null, null, Map.of()), now, now, now);
    }

    private record ValidatedState(
            ForgotPasswordOtpResendEligibilityStatus status,
            ForgotPasswordAuthFlowContext context,
            OtpChallenge authoritativeChallenge,
            int successfulResendCount,
            Integer retryAfterSeconds,
            String maskedDestination,
            Integer expiresInSeconds,
            boolean securityRejected) {
        static ValidatedState invalid(ForgotPasswordOtpResendEligibilityStatus status) {
            return invalid(status, false, null, null);
        }

        static ValidatedState invalid(ForgotPasswordOtpResendEligibilityStatus status, boolean securityRejected) {
            return invalid(status, securityRejected, null, null);
        }

        static ValidatedState invalid(ForgotPasswordOtpResendEligibilityStatus status, boolean securityRejected,
                String maskedDestination, Integer expiresInSeconds) {
            return new ValidatedState(status, null, null, 0, null, maskedDestination, expiresInSeconds,
                    securityRejected);
        }
    }
}