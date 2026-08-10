package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Error;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Kind;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;


/**
 * Application orchestration for the Username Recovery start flow (Task 019).
 *
 * <p>Design references: {@code .cline/tasks/019-start-use-case.md} required orchestration order
 * (validate, fingerprint, reserve, classify, consume verified captcha, lookup, zero-registered
 * no-session or registered session creation, safe replay completion) and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} sections 7-8
 * (UR-START-001..015).
 *
 * <p>Behavioral contract:
 *
 * <ul>
 *   <li>Only a typed command that passes Registration-compatible validation is processed;
 *       validation, idempotency conflict, captcha rejection, and definite system failures are
 *       typed {@link UsernameRecoveryStartException} signals.</li>
 *   <li>START idempotency is reserved after fingerprinting; in-progress, key-reused,
 *       unknown-outcome, completed-replay, and system outcomes are handled distinctly.</li>
 *   <li>Only a {@code VERIFIED} captcha challenge is atomically consumed, and only after a
 *       successful reservation. The challenge stays consumed if a later step fails.</li>
 *   <li>Lookup runs only after reservation and consumption. Zero registered candidates create
 *       no session, CSRF token, or Scheme-Refs and return {@code ZERO_REGISTERED}. Registered
 *       candidates create a minimal ACTIVE protected session (opaque Scheme-Refs, hashed CSRF,
 *       600-second expiry) and complete idempotency with the committed 019B codec snapshot.</li>
 *   <li>Same-key completed replay resolves the committed snapshot: the codec session id reloads
 *       and rotates the CSRF hash through expected-revision CAS without extending the TTL; the
 *       non-session codec reconstructs {@code MEMBER_NOT_FOUND}/{@code ZERO_REGISTERED}.
 *       Malformed, missing, absent-session, expired, or terminal replay states fail closed as
 *       {@code SYSTEM_FAILURE} and never blind-create a second session.</li>
 *   <li>No automatic retry and no catch-all error collapse: port infrastructure failures are
 *       wrapped in {@link StartPersistenceException} and mapped to typed {@code SYSTEM_FAILURE};
 *       unexpected programming defects propagate to the central exception boundary.</li>
 * </ul>
 */
public class StartUsernameRecoveryService implements StartUsernameRecoveryUseCase {

    private final UsernameRecoveryStartValidator validator;
    private final UsernameRecoveryStartFingerprintGenerator fingerprintGenerator;
    private final UsernameRecoveryIdempotencyPort idempotencyPort;
    private final RegistrationCaptchaGateService captchaGateService;
    private final UsernameRecoveryLookupPort lookupPort;
    private final UsernameRecoverySessionPort sessionPort;
    private final CsrfTokenGenerator csrfTokenGenerator;
    private final CsrfTokenHasher csrfTokenHasher;
    private final Clock clock;
    private final int ttlSeconds;
    private final UsernameRecoveryStartSchemeRefGenerator schemeRefGenerator =
            new UsernameRecoveryStartSchemeRefGenerator();
    private final SecureRandom secureRandom = new SecureRandom();

    public StartUsernameRecoveryService(
            UsernameRecoveryStartValidator validator,
            UsernameRecoveryStartFingerprintGenerator fingerprintGenerator,
            UsernameRecoveryIdempotencyPort idempotencyPort,
            RegistrationCaptchaGateService captchaGateService,
            UsernameRecoveryLookupPort lookupPort,
            UsernameRecoverySessionPort sessionPort,
            CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher,
            Clock clock,
            int ttlSeconds) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.fingerprintGenerator = Objects.requireNonNull(
                fingerprintGenerator, "fingerprintGenerator must not be null");
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort, "idempotencyPort must not be null");
        this.captchaGateService = Objects.requireNonNull(
                captchaGateService, "captchaGateService must not be null");
        this.lookupPort = Objects.requireNonNull(lookupPort, "lookupPort must not be null");
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort must not be null");
        this.csrfTokenGenerator = Objects.requireNonNull(
                csrfTokenGenerator, "csrfTokenGenerator must not be null");
        this.csrfTokenHasher = Objects.requireNonNull(csrfTokenHasher, "csrfTokenHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Mono<UsernameRecoveryStartResult> start(UsernameRecoveryStartCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UsernameRecoveryStartValidator.Validation validation = validator.validate(command);
        if (!validation.valid()) {
            return Mono.error(UsernameRecoveryStartException.validationFailed(validation.errors()));
        }

        UsernameRecoveryIdempotencyFingerprint fingerprint = fingerprintGenerator.derive(command);
        UsernameRecoveryIdempotencyKey idempotencyKey;
        try {
            idempotencyKey = new UsernameRecoveryIdempotencyKey(command.idempotencyKey());
        } catch (IllegalArgumentException exception) {
            return Mono.error(UsernameRecoveryStartException.validationFailed(List.of(
                    new Error("idempotencyKey", Kind.IDEMPOTENCY_KEY_INVALID))));
        }

        Instant now = clock.instant();
        UsernameRecoveryIdempotencyOperationReference operationReference = generateOperationReference();
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        UsernameRecoveryIdempotencyPort.ReserveCommand reserveCommand =
                new UsernameRecoveryIdempotencyPort.ReserveCommand(
                        UsernameRecoveryIdempotencyAction.START,
                        idempotencyKey,
                        fingerprint,
                        operationReference,
                        now,
                        ttl);

        return idempotencyPort.reserve(reserveCommand)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(reservation -> handleReservation(
                        command, validation, idempotencyKey, fingerprint, now, ttl, reservation))
                .onErrorResume(StartPersistenceException.class,
                        error -> Mono.error(UsernameRecoveryStartException.systemFailure(error)));
    }

    private Mono<UsernameRecoveryStartResult> handleReservation(
            UsernameRecoveryStartCommand command,
            UsernameRecoveryStartValidator.Validation validation,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            Instant now,
            Duration ttl,
            UsernameRecoveryIdempotencyPort.ReservationResult reservation) {
        return switch (reservation.outcome()) {
            case RESERVED -> proceed(command, validation, idempotencyKey, fingerprint,
                    reservation.reservation(), now, ttl);
            case COMPLETED_REPLAY -> replay(command, now);
            case DUPLICATE_IN_PROGRESS -> Mono.error(UsernameRecoveryStartException.idempotencyInProgress());
            case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.error(UsernameRecoveryStartException.keyReused());
            case UNKNOWN_OUTCOME_EXISTING -> Mono.error(UsernameRecoveryStartException.unknownOutcomeExisting());
            case SYSTEM_FAILURE -> Mono.error(UsernameRecoveryStartException.systemFailure());
            default -> throw new IllegalStateException(
                    "unexpected idempotency reserve outcome: " + reservation.outcome());
        };
    }


    private Mono<UsernameRecoveryStartResult> proceed(
            UsernameRecoveryStartCommand command,
            UsernameRecoveryStartValidator.Validation validation,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        CaptchaChallengeId captchaChallengeId;
        try {
            captchaChallengeId = new CaptchaChallengeId(command.captchaChallengeId());
        } catch (IllegalArgumentException exception) {
            return Mono.error(UsernameRecoveryStartException.captchaInvalid());
        }
        ConsumeCaptchaChallengeCommand consumeCommand =
                new ConsumeCaptchaChallengeCommand(captchaChallengeId, now);
        return captchaGateService.consume(consumeCommand)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(consumeResult -> switch (consumeResult.outcome()) {
                    case CONSUMED -> lookup(command, validation, idempotencyKey, fingerprint,
                            reservation, now, ttl);
                    case NOT_FOUND -> Mono.error(UsernameRecoveryStartException.captchaNotFound());
                    case NOT_VERIFIED -> Mono.error(UsernameRecoveryStartException.captchaNotVerified());
                    case EXPIRED -> Mono.error(UsernameRecoveryStartException.captchaExpired());
                    case LOCKED, ALREADY_CONSUMED, REFRESHED ->
                            Mono.error(UsernameRecoveryStartException.captchaConflict());
                    default -> throw new IllegalStateException(
                            "unexpected captcha consume outcome: " + consumeResult.outcome());
                });
    }

    private Mono<UsernameRecoveryStartResult> lookup(
            UsernameRecoveryStartCommand command,
            UsernameRecoveryStartValidator.Validation validation,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        UsernameRecoveryLookupCommand lookupCommand = new UsernameRecoveryLookupCommand(
                command.identityType(), command.identityValue(), command.dateOfBirth(),
                validation.email(), validation.mobile());
        return lookupPort.search(lookupCommand)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(lookupResult -> switch (lookupResult.status()) {
                    case MATCHED -> handleMatched(command, validation, idempotencyKey, fingerprint,
                            reservation, now, ttl, lookupResult.candidates());
                    case NOT_FOUND -> completeNonSession(idempotencyKey, fingerprint, reservation, now, ttl,
                            new UsernameRecoveryStartResult(
                                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null, null, List.of()));
                    case CONTRACT_INVALID, UNAVAILABLE, UNKNOWN_OUTCOME ->
                            Mono.error(UsernameRecoveryStartException.systemFailure());
                    default -> throw new IllegalStateException(
                            "unexpected lookup status: " + lookupResult.status());
                });
    }

    private Mono<UsernameRecoveryStartResult> handleMatched(
            UsernameRecoveryStartCommand command,
            UsernameRecoveryStartValidator.Validation validation,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl,
            List<UsernameRecoveryLookupCandidate> candidates) {
        boolean anyRegistered = candidates.stream()
                .anyMatch(UsernameRecoveryLookupCandidate::registered);
        if (!anyRegistered) {
            List<UsernameRecoverySchemeProjection> projections = candidates.stream()
                    .map(candidate -> new UsernameRecoverySchemeProjection(
                            null, candidate.accountEnv(), candidate.trustCode(), false))
                    .toList();
            return completeNonSession(idempotencyKey, fingerprint, reservation, now, ttl,
                    new UsernameRecoveryStartResult(
                            UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED, null, null, projections));
        }
        return createSession(command, validation, candidates, now)
                .flatMap(draft -> completeSessionCreated(
                        idempotencyKey, fingerprint, reservation, now, ttl, draft));
    }


    private Mono<SessionDraft> createSession(
            UsernameRecoveryStartCommand command,
            UsernameRecoveryStartValidator.Validation validation,
            List<UsernameRecoveryLookupCandidate> candidates,
            Instant now) {
        String rawToken = csrfTokenGenerator.generate();
        UsernameRecoveryCsrfHash csrfHash = new UsernameRecoveryCsrfHash(csrfTokenHasher.hash(rawToken));
        UsernameRecoverySessionId sessionId = generateSessionId();
        List<UsernameRecoverySessionCandidate> sessionCandidates = candidates.stream()
                .map(candidate -> {
                    UsernameRecoverySchemeRef schemeRef = schemeRefGenerator.generate();
                    String userName = candidate.registered() ? candidate.userName() : null;
                    return new UsernameRecoverySessionCandidate(
                            new UsernameRecoveryCandidate(
                                    schemeRef,
                                    candidate.accountEnv(),
                                    candidate.trustCode(),
                                    candidate.registered(),
                                    userName,
                                    UsernameRecoveryDeliveryState.AVAILABLE),
                            null,
                            null);
                })
                .toList();
        UsernameRecoverySession session = new UsernameRecoverySession(
                sessionId,
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                csrfHash,
                validation.destination(),
                command.language(),
                now,
                now.plusSeconds(ttlSeconds),
                sessionCandidates);
        return sessionPort.create(session)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(result -> switch (result.outcome()) {
                    case CREATED -> Mono.just(new SessionDraft(session, rawToken));
                    case SESSION_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                            Mono.error(UsernameRecoveryStartException.systemFailure());
                    default -> throw new IllegalStateException(
                            "unexpected session create outcome: " + result.outcome());
                });
    }

    private Mono<UsernameRecoveryStartResult> completeSessionCreated(
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl,
            SessionDraft draft) {
        ReplaySnapshot snapshot = UsernameRecoveryStartReplayCodec.encode(draft.session().sessionId());
        UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand =
                new UsernameRecoveryIdempotencyPort.CompleteCommand(
                        UsernameRecoveryIdempotencyAction.START,
                        idempotencyKey,
                        fingerprint,
                        reservation.operationReference(),
                        snapshot,
                        now,
                        ttl);
        return idempotencyPort.complete(completeCommand)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .map(record -> sessionCreatedResult(draft.session(), draft.rawToken(), now));
    }

    private Mono<UsernameRecoveryStartResult> completeNonSession(
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl,
            UsernameRecoveryStartResult result) {
        ReplaySnapshot snapshot = UsernameRecoveryStartReplaySnapshot.encode(result);
        UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand =
                new UsernameRecoveryIdempotencyPort.CompleteCommand(
                        UsernameRecoveryIdempotencyAction.START,
                        idempotencyKey,
                        fingerprint,
                        reservation.operationReference(),
                        snapshot,
                        now,
                        ttl);
        return idempotencyPort.complete(completeCommand)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .map(record -> result);
    }


    private Mono<UsernameRecoveryStartResult> replay(UsernameRecoveryStartCommand command, Instant now) {
        UsernameRecoveryIdempotencyKey idempotencyKey;
        try {
            idempotencyKey = new UsernameRecoveryIdempotencyKey(command.idempotencyKey());
        } catch (IllegalArgumentException exception) {
            return Mono.error(UsernameRecoveryStartException.validationFailed(List.of()));
        }
        return idempotencyPort.resolve(UsernameRecoveryIdempotencyAction.START, idempotencyKey)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return Mono.error(UsernameRecoveryStartException.systemFailure());
                    }
                    ReplaySnapshot snapshot = optional.get().replaySnapshot();
                    if (snapshot == null) {
                        return Mono.error(UsernameRecoveryStartException.systemFailure());
                    }
                    UsernameRecoveryStartReplayCodec.DecodeResult sessionDecode =
                            UsernameRecoveryStartReplayCodec.decode(snapshot);
                    if (sessionDecode.decoded()) {
                        return replaySessionCreated(sessionDecode.sessionId(), now);
                    }
                    UsernameRecoveryStartReplaySnapshot.DecodeResult nonSessionDecode =
                            UsernameRecoveryStartReplaySnapshot.decode(snapshot);
                    if (nonSessionDecode.decoded()) {
                        return Mono.just(nonSessionDecode.result());
                    }
                    return Mono.error(UsernameRecoveryStartException.systemFailure());
                });
    }

    private Mono<UsernameRecoveryStartResult> replaySessionCreated(
            UsernameRecoverySessionId sessionId, Instant now) {
        return sessionPort.load(sessionId)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(loadResult -> switch (loadResult.outcome()) {
                    case LOADED -> rotateAndRebuild(loadResult.session(), now);
                    case SESSION_NOT_FOUND -> Mono.error(UsernameRecoveryStartException.systemFailure());
                    case SYSTEM_FAILURE -> Mono.error(UsernameRecoveryStartException.systemFailure());
                    default -> throw new IllegalStateException(
                            "unexpected session load outcome: " + loadResult.outcome());
                });
    }

    private Mono<UsernameRecoveryStartResult> rotateAndRebuild(
            UsernameRecoverySession session, Instant now) {
        if (!session.expiresAt().isAfter(now)) {
            return Mono.error(UsernameRecoveryStartException.systemFailure());
        }
        if (session.isTerminal()) {
            return Mono.error(UsernameRecoveryStartException.systemFailure());
        }
        String rawToken = csrfTokenGenerator.generate();
        UsernameRecoveryCsrfHash newHash = new UsernameRecoveryCsrfHash(csrfTokenHasher.hash(rawToken));
        UsernameRecoverySession updated = new UsernameRecoverySession(
                session.sessionId(),
                session.scenario(),
                session.status(),
                session.revision().next(),
                newHash,
                session.destination(),
                session.language(),
                session.createdAt(),
                session.expiresAt(),
                session.candidates());
        UsernameRecoverySessionMutationCommand mutation = new UsernameRecoverySessionMutationCommand(
                session.sessionId(), session.revision(), updated);
        return sessionPort.update(mutation)
                .onErrorMap(Exception.class, StartPersistenceException::new)
                .flatMap(result -> switch (result.outcome()) {
                    case APPLIED -> Mono.just(sessionCreatedResult(updated, rawToken, now));
                    case SESSION_NOT_FOUND, SYSTEM_FAILURE ->
                            Mono.error(UsernameRecoveryStartException.systemFailure());
                    case REVISION_CONFLICT ->
                            Mono.error(UsernameRecoveryStartException.replayRevisionConflict());
                    default -> throw new IllegalStateException(
                            "unexpected session update outcome: " + result.outcome());
                });
    }


    private UsernameRecoveryStartResult sessionCreatedResult(
            UsernameRecoverySession session, String rawToken, Instant now) {
        List<UsernameRecoverySchemeProjection> projections = session.candidates().stream()
                .map(candidate -> new UsernameRecoverySchemeProjection(
                        candidate.schemeRef(), candidate.accountEnv(), candidate.trustCode(),
                        candidate.registered()))
                .toList();
        long remainingSeconds = Duration.between(now, session.expiresAt()).toSeconds();
        int expiresInSeconds = (int) Math.max(1, remainingSeconds);
        return new UsernameRecoveryStartResult(
                UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                new AuthFlowSessionId(session.sessionId().value()),
                rawToken,
                expiresInSeconds,
                projections);
    }

    private UsernameRecoverySessionId generateSessionId() {
        return new UsernameRecoverySessionId(
                "afs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(18)));
    }

    private UsernameRecoveryIdempotencyOperationReference generateOperationReference() {
        return new UsernameRecoveryIdempotencyOperationReference(
                "op_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(18)));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private record SessionDraft(UsernameRecoverySession session, String rawToken) {
    }

    private static final class StartPersistenceException extends RuntimeException {
        private StartPersistenceException(Throwable cause) {
            super("Username recovery start persistence failed", cause);
        }
    }
}

