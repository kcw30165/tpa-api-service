package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Application orchestration for the Username Recovery cancel flow (Task 021).
 *
 * <p>
 * Design references: {@code .cline/tasks/021-cancel-use-case.md} canonical orchestration order
 * (validate, fingerprint, reserve, classify, origin/referer, load, absent-session idempotent
 * success, live-session CSRF gate, definitive delete, idempotency completion) and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
 * (UR-CANCEL-001 valid or absent session -&gt; 200 SUCCESS delete/clear idempotent; UR-CANCEL-002
 * invalid CSRF with live session -&gt; 403 no mutation; UR-CANCEL-003 mapped infrastructure
 * exception -&gt; uncertain, same-key retry) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 8
 * (failed cleanup must never report successful deletion without a definitive idempotent outcome).
 *
 * <p>
 * Behavioral contract:
 *
 * <ul>
 *   <li>The committed inbound-port method {@link #cancel(UsernameRecoveryCancelCommand)} fails
 *       closed with {@code SECURITY_CONTEXT_REQUIRED} because the port cannot carry raw CSRF;
 *       the richer method is the canonical entry.</li>
 *   <li>Raw CSRF and origin/referer enter only at the richer method boundary, are never stored,
 *       and are never logged or exposed by any {@code toString()}.</li>
 *   <li>CANCEL idempotency is reserved after fingerprinting; in-progress, key-reused,
 *       unknown-outcome-existing, completed-replay, and system outcomes are handled distinctly.</li>
 *   <li>A genuinely absent session is an idempotent cancellation success (UR-CANCEL-001) with no
 *       CSRF requirement and no delete.</li>
 *   <li>A present live session requires origin/referer (when policy requires) and a raw CSRF match
 *       before any deletion; invalid/missing CSRF rejects without mutation (UR-CANCEL-002).</li>
 *   <li>A present but not live session (expired or terminal) is deleted through the committed
 *       session boundary for cleanup without a CSRF gate - UR-CANCEL-002 defines the CSRF failure
 *       only for a live session, and the canonical order applies the CSRF gate only to present and
 *       live sessions.</li>
 *   <li>Success is returned only after a definitive delete outcome ({@code DELETED} or
 *       {@code ALREADY_ABSENT}) and a completed idempotency record; uncertain deletion records a
 *       stable unknown outcome and never claims success; deletion is never retried automatically.</li>
 *   <li>A completed replay resolves the constant CANCEL descriptor and returns success without
 *       loading or deleting the session; malformed/contradictory replay fails closed and never
 *       deletes again.</li>
 *   <li>The application service never clears or writes cookies; exact-path cookie clearing belongs
 *       to the later web/controller task.</li>
 *   <li>Unexpected defects (default branches, programming {@code Error}s) propagate to the central
 *       exception boundary; no silent catch or exception-to-null fallback exists.</li>
 * </ul>
 */
public final class CancelUsernameRecoveryService implements CancelUsernameRecoveryUseCase {

    private final UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator;
    private final UsernameRecoveryIdempotencyPort idempotencyPort;
    private final UsernameRecoverySessionPort sessionPort;
    private final OriginRefererValidator originRefererValidator;
    private final CsrfTokenValidationService csrfTokenValidationService;
    private final List<String> allowedOrigins;
    private final Clock clock;
    private final int ttlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public CancelUsernameRecoveryService(
            UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator,
            UsernameRecoveryIdempotencyPort idempotencyPort,
            UsernameRecoverySessionPort sessionPort,
            CsrfTokenValidationService csrfTokenValidationService,
            List<String> allowedOrigins,
            Clock clock,
            int ttlSeconds) {
        this.fingerprintGenerator = Objects.requireNonNull(fingerprintGenerator,
                "fingerprintGenerator must not be null");
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort,
                "idempotencyPort must not be null");
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort must not be null");
        this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService,
                "csrfTokenValidationService must not be null");
        this.originRefererValidator = new OriginRefererValidator();
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Committed inbound-port shape. The port cannot carry the raw CSRF token (018A decision), so
     * this method fails closed: cancellation must never proceed without CSRF validation.
     */
    @Override
    public Mono<UsernameRecoveryCancelResult> cancel(UsernameRecoveryCancelCommand command) {
        return Mono.error(UsernameRecoveryCancelException.securityContextRequired());
    }

    /**
     * Canonical orchestration entry where the raw CSRF token and origin/referer enter at the
     * method boundary. They are never stored in any record, command, field, log, exception, or
     * {@code toString()}.
     */
    public Mono<UsernameRecoveryCancelResult> cancel(
            UsernameRecoveryCancelCommand command,
            String rawCsrfToken,
            String origin,
            String referer) {
        if (command == null) {
            return Mono.error(UsernameRecoveryCancelException.validationFailed());
        }
        UsernameRecoveryIdempotencyKey idempotencyKey;
        try {
            idempotencyKey = new UsernameRecoveryIdempotencyKey(command.idempotencyKey());
        } catch (IllegalArgumentException exception) {
            return Mono.error(UsernameRecoveryCancelException.validationFailed());
        }
        UsernameRecoveryIdempotencyFingerprint fingerprint = fingerprintGenerator.derive(command);
        Instant now = clock.instant();
        UsernameRecoveryIdempotencyOperationReference operationReference = generateOperationReference();
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        UsernameRecoveryIdempotencyPort.ReserveCommand reserveCommand =
                new UsernameRecoveryIdempotencyPort.ReserveCommand(
                        UsernameRecoveryIdempotencyAction.CANCEL,
                        idempotencyKey,
                        fingerprint,
                        operationReference,
                        now,
                        ttl);
        return idempotencyPort.reserve(reserveCommand)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(reservation -> handleReservation(
                        command,
                        rawCsrfToken,
                        origin,
                        referer,
                        idempotencyKey,
                        fingerprint,
                        now,
                        ttl,
                        reservation))
                .onErrorResume(CancelPersistenceException.class,
                        error -> Mono.error(UsernameRecoveryCancelException.systemFailure(error)));
    }


    private Mono<UsernameRecoveryCancelResult> handleReservation(
            UsernameRecoveryCancelCommand command,
            String rawCsrfToken,
            String origin,
            String referer,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            Instant now,
            Duration ttl,
            UsernameRecoveryIdempotencyPort.ReservationResult reservation) {
        return switch (reservation.outcome()) {
            case RESERVED -> proceed(command, rawCsrfToken, origin, referer, idempotencyKey,
                    fingerprint, reservation.reservation(), now, ttl);
            case COMPLETED_REPLAY -> replay(idempotencyKey);
            case DUPLICATE_IN_PROGRESS ->
                    Mono.error(UsernameRecoveryCancelException.idempotencyInProgress());
            case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                    Mono.error(UsernameRecoveryCancelException.keyReused());
            case UNKNOWN_OUTCOME_EXISTING ->
                    Mono.error(UsernameRecoveryCancelException.unknownOutcomeExisting());
            case SYSTEM_FAILURE -> Mono.error(UsernameRecoveryCancelException.systemFailure());
            default -> throw new IllegalStateException(
                    "unexpected idempotency reserve outcome: " + reservation.outcome());
        };
    }

    private Mono<UsernameRecoveryCancelResult> proceed(
            UsernameRecoveryCancelCommand command,
            String rawCsrfToken,
            String origin,
            String referer,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        UsernameRecoverySessionId sessionId =
                new UsernameRecoverySessionId(command.authFlowSessionId().value());
        return sessionPort.load(sessionId)
                .onErrorResume(Exception.class,
                        error -> Mono.just(UsernameRecoverySessionLoadResult.systemFailure()))
                .flatMap(loadResult -> switch (loadResult.outcome()) {
                    case SESSION_NOT_FOUND ->
                            completeIdempotency(idempotencyKey, fingerprint, reservation, now, ttl);
                    case SYSTEM_FAILURE -> recordUnknownOutcome(
                    idempotencyKey, fingerprint, reservation, now, ttl);
                    case LOADED -> handleLoaded(loadResult.session(), rawCsrfToken, origin, referer,
                        idempotencyKey,
                            fingerprint, reservation, now, ttl);
                    default -> throw new IllegalStateException(
                            "unexpected session load outcome: " + loadResult.outcome());
                });
    }

    private Mono<UsernameRecoveryCancelResult> handleLoaded(
            UsernameRecoverySession session,
            String rawCsrfToken,
            String origin,
            String referer,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        if (!session.isExpired(now) && !session.isTerminal()) {
            if (!originRefererAllowed(origin, referer)) {
                return Mono.error(UsernameRecoveryCancelException.securityInvalid());
            }
            // Present and live: the raw CSRF token must match the stored hash before any
            // deletion (UR-CANCEL-002). The raw token is consumed only here and never stored.
            CsrfTokenValidationResult validation;
            try {
                validation = csrfTokenValidationService.validate(
                        rawCsrfToken, session.csrfHash().value());
            } catch (Exception exception) {
                return Mono.error(UsernameRecoveryCancelException.systemFailure());
            }
            if (!validation.valid()) {
                return Mono.error(UsernameRecoveryCancelException.securityInvalid());
            }
        }
        // Present but not live (expired or terminal): no CSRF gate; deletion is harmless cleanup
        // of a dead record through the committed idempotent session boundary.
        return deleteAndSettle(session, idempotencyKey, fingerprint, reservation, now, ttl);
    }

    private Mono<UsernameRecoveryCancelResult> deleteAndSettle(
            UsernameRecoverySession session,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        return sessionPort.delete(session.sessionId())
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(deleteResult -> switch (deleteResult.outcome()) {
                    case DELETED, ALREADY_ABSENT ->
                            completeIdempotency(idempotencyKey, fingerprint, reservation, now, ttl);
                    case SYSTEM_FAILURE -> Mono.error(new CancelPersistenceException(
                            new IllegalStateException("session delete outcome uncertain")));
                    default -> throw new IllegalStateException(
                            "unexpected session delete outcome: " + deleteResult.outcome());
                })
                .onErrorResume(CancelPersistenceException.class,
                        error -> recordUnknownOutcome(
                                idempotencyKey, fingerprint, reservation, now, ttl));
    }


    private Mono<UsernameRecoveryCancelResult> completeIdempotency(
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        ReplaySnapshot snapshot = UsernameRecoveryCancelReplayCodec.encode();
        UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand =
                new UsernameRecoveryIdempotencyPort.CompleteCommand(
                        UsernameRecoveryIdempotencyAction.CANCEL,
                        idempotencyKey,
                        fingerprint,
                        reservation.operationReference(),
                        snapshot,
                        now,
                        ttl);
        return idempotencyPort.complete(completeCommand)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .map(record -> new UsernameRecoveryCancelResult(
                        UsernameRecoveryCancelResult.Outcome.SUCCESS));
    }

    private Mono<UsernameRecoveryCancelResult> recordUnknownOutcome(
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation,
            Instant now,
            Duration ttl) {
        UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand unknownOutcomeCommand =
                new UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand(
                        UsernameRecoveryIdempotencyAction.CANCEL,
                        idempotencyKey,
                        fingerprint,
                        reservation.operationReference(),
                        now,
                        ttl);
        return idempotencyPort.recordUnknownOutcome(unknownOutcomeCommand)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .map(record -> new UsernameRecoveryCancelResult(
                        UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED));
    }

    private Mono<UsernameRecoveryCancelResult> replay(
            UsernameRecoveryIdempotencyKey idempotencyKey) {
        return idempotencyPort.resolve(UsernameRecoveryIdempotencyAction.CANCEL, idempotencyKey)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return Mono.error(UsernameRecoveryCancelException.systemFailure());
                    }
                    var record = optional.get();
            if (record.action() != UsernameRecoveryIdempotencyAction.CANCEL
                    || !record.idempotencyKey().equals(idempotencyKey)
                    || record.status() != UsernameRecoveryIdempotencyRecord.Status.COMPLETED) {
                return Mono.error(UsernameRecoveryCancelException.systemFailure());
            }
            ReplaySnapshot snapshot = record.replaySnapshot();
                    if (snapshot == null) {
                        return Mono.error(UsernameRecoveryCancelException.systemFailure());
                    }
                    if (!UsernameRecoveryCancelReplayCodec.decode(snapshot).decoded()) {
                        return Mono.error(UsernameRecoveryCancelException.systemFailure());
                    }
                    return Mono.just(new UsernameRecoveryCancelResult(
                            UsernameRecoveryCancelResult.Outcome.SUCCESS));
                });
    }

    private boolean originRefererAllowed(String origin, String referer) {
        return allowedOrigins.isEmpty()
                || originRefererValidator.validate(origin, referer, allowedOrigins).valid();
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

    private static final class CancelPersistenceException extends RuntimeException {
        private CancelPersistenceException(Throwable cause) {
            super("Username recovery cancel persistence failed", cause);
        }
    }
}

