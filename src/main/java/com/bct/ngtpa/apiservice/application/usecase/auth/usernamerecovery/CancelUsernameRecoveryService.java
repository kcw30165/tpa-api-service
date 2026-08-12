package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionDeleteCommand;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStatePort;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Model B cancellation orchestration for Username Recovery. */
public final class CancelUsernameRecoveryService implements CancelUsernameRecoveryUseCase {
    private final UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator;
    private final UsernameRecoveryIdempotencyPort idempotencyPort;
    private final AuthFlowSessionPort authFlowSessionPort;
    private final UsernameRecoveryWorkflowStatePort workflowStatePort;
    private final OriginRefererValidator originRefererValidator = new OriginRefererValidator();
    private final CsrfTokenValidationService csrfTokenValidationService;
    private final List<String> allowedOrigins;
    private final Clock clock;
    private final int ttlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public CancelUsernameRecoveryService(
            UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator,
            UsernameRecoveryIdempotencyPort idempotencyPort,
            AuthFlowSessionPort authFlowSessionPort,
            UsernameRecoveryWorkflowStatePort workflowStatePort,
            CsrfTokenValidationService csrfTokenValidationService,
            List<String> allowedOrigins,
            Clock clock,
            int ttlSeconds) {
        this.fingerprintGenerator = Objects.requireNonNull(fingerprintGenerator, "fingerprintGenerator must not be null");
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort, "idempotencyPort must not be null");
        this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
        this.workflowStatePort = Objects.requireNonNull(workflowStatePort, "workflowStatePort must not be null");
        this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService, "csrfTokenValidationService must not be null");
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Mono<UsernameRecoveryCancelResult> cancel(UsernameRecoveryCancelCommand command) {
        return Mono.error(UsernameRecoveryCancelException.securityContextRequired());
    }

    public Mono<UsernameRecoveryCancelResult> cancel(
            UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer) {
        if (command == null) return Mono.error(UsernameRecoveryCancelException.validationFailed());
        final UsernameRecoveryIdempotencyKey key;
        try { key = new UsernameRecoveryIdempotencyKey(command.idempotencyKey()); }
        catch (IllegalArgumentException error) { return Mono.error(UsernameRecoveryCancelException.validationFailed()); }
        var fingerprint = fingerprintGenerator.derive(command);
        var now = clock.instant();
        var operation = generateOperationReference();
        var ttl = Duration.ofSeconds(ttlSeconds);
        var reserve = new UsernameRecoveryIdempotencyPort.ReserveCommand(
                UsernameRecoveryIdempotencyAction.CANCEL, key, fingerprint, operation, now, ttl);
        return idempotencyPort.reserve(reserve)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(result -> handleReservation(command, rawCsrfToken, origin, referer,
                        key, fingerprint, now, ttl, result))
                .onErrorResume(CancelPersistenceException.class,
                        error -> Mono.error(UsernameRecoveryCancelException.systemFailure(error)));
    }

    private Mono<UsernameRecoveryCancelResult> handleReservation(
            UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer,
            UsernameRecoveryIdempotencyKey key, UsernameRecoveryIdempotencyFingerprint fingerprint,
            Instant now, Duration ttl, UsernameRecoveryIdempotencyPort.ReservationResult result) {
        return switch (result.outcome()) {
            case RESERVED -> proceed(command, rawCsrfToken, origin, referer, key, fingerprint,
                    result.reservation(), now, ttl);
            case COMPLETED_REPLAY -> replay(key);
            case DUPLICATE_IN_PROGRESS -> Mono.error(UsernameRecoveryCancelException.idempotencyInProgress());
            case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.error(UsernameRecoveryCancelException.keyReused());
            case UNKNOWN_OUTCOME_EXISTING -> Mono.error(UsernameRecoveryCancelException.unknownOutcomeExisting());
            case SYSTEM_FAILURE -> Mono.error(UsernameRecoveryCancelException.systemFailure());
            default -> throw new IllegalStateException("unexpected idempotency reserve outcome: " + result.outcome());
        };
    }

    private Mono<UsernameRecoveryCancelResult> proceed(
            UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer,
            UsernameRecoveryIdempotencyKey key, UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation, Instant now, Duration ttl) {
        return authFlowSessionPort.loadSnapshot(command.authFlowSessionId())
                .onErrorResume(Exception.class, error -> Mono.error(new CancelPersistenceException(error)))
                .flatMap(optional -> optional
                        .<Mono<UsernameRecoveryCancelResult>>map(snapshot -> handleEnvelope(snapshot,
                                command, rawCsrfToken, origin, referer, key, fingerprint,
                                reservation, now, ttl))
                        .orElseGet(() -> deleteSidecarAndSettle(command, key, fingerprint,
                                reservation, now, ttl)))
                .onErrorResume(CancelPersistenceException.class,
                        error -> recordUnknownOutcome(key, fingerprint, reservation, now, ttl));
    }

    private Mono<UsernameRecoveryCancelResult> handleEnvelope(
            AuthFlowSessionSnapshot snapshot, UsernameRecoveryCancelCommand command,
            String rawCsrfToken, String origin, String referer,
            UsernameRecoveryIdempotencyKey key, UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation, Instant now, Duration ttl) {
        var session = snapshot.session();
        if (!session.authFlowSessionId().equals(command.authFlowSessionId())
                || session.scenario() != AuthFlowScenario.USERNAME_RECOVERY) {
            return Mono.error(UsernameRecoveryCancelException.systemFailure());
        }
        if (!session.isExpired(now) && !session.isTerminal()) {
            if (!originRefererAllowed(origin, referer)) {
                return Mono.error(UsernameRecoveryCancelException.securityInvalid());
            }
            final CsrfTokenValidationResult validation;
            try { validation = csrfTokenValidationService.validate(rawCsrfToken, session.csrfTokenHash()); }
            catch (Exception error) { return Mono.error(UsernameRecoveryCancelException.systemFailure()); }
            if (!validation.valid()) return Mono.error(UsernameRecoveryCancelException.securityInvalid());
        }
        return authFlowSessionPort.deleteRevisioned(new RevisionedAuthFlowSessionDeleteCommand(snapshot))
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(result -> {
                    if (result.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                            || result.outcome() == AuthFlowSessionMutationOutcome.SESSION_NOT_FOUND) {
                        return deleteSidecarAndSettle(command, key, fingerprint, reservation, now, ttl);
                    }
                    return Mono.error(new CancelPersistenceException(
                            new IllegalStateException("auth-flow delete outcome uncertain")));
                });
    }

    private Mono<UsernameRecoveryCancelResult> deleteSidecarAndSettle(
            UsernameRecoveryCancelCommand command, UsernameRecoveryIdempotencyKey key,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation, Instant now, Duration ttl) {
        return workflowStatePort.delete(command.authFlowSessionId())
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(result -> {
                    var outcome = result.outcome();
                    if (outcome == UsernameRecoveryWorkflowStateMutationOutcome.DELETED
                            || outcome == UsernameRecoveryWorkflowStateMutationOutcome.ALREADY_ABSENT) {
                        return completeIdempotency(key, fingerprint, reservation, now, ttl);
                    }
                    return Mono.error(new CancelPersistenceException(
                            new IllegalStateException("workflow-state delete outcome uncertain")));
                });
    }

    private Mono<UsernameRecoveryCancelResult> completeIdempotency(
            UsernameRecoveryIdempotencyKey key, UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation, Instant now, Duration ttl) {
        ReplaySnapshot snapshot = UsernameRecoveryCancelReplayCodec.encode();
        var command = new UsernameRecoveryIdempotencyPort.CompleteCommand(
                UsernameRecoveryIdempotencyAction.CANCEL, key, fingerprint,
                reservation.operationReference(), snapshot, now, ttl);
        return idempotencyPort.complete(command)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .map(record -> new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));
    }

    private Mono<UsernameRecoveryCancelResult> recordUnknownOutcome(
            UsernameRecoveryIdempotencyKey key, UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyReservation reservation, Instant now, Duration ttl) {
        var command = new UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand(
                UsernameRecoveryIdempotencyAction.CANCEL, key, fingerprint,
                reservation.operationReference(), now, ttl);
        return idempotencyPort.recordUnknownOutcome(command)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .map(record -> new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED));
    }

    private Mono<UsernameRecoveryCancelResult> replay(UsernameRecoveryIdempotencyKey key) {
        return idempotencyPort.resolve(UsernameRecoveryIdempotencyAction.CANCEL, key)
                .onErrorMap(Exception.class, CancelPersistenceException::new)
                .flatMap(optional -> {
                    if (optional.isEmpty()) return Mono.error(UsernameRecoveryCancelException.systemFailure());
                    var record = optional.get();
                    if (record.action() != UsernameRecoveryIdempotencyAction.CANCEL
                            || !record.idempotencyKey().equals(key)
                            || record.status() != UsernameRecoveryIdempotencyRecord.Status.COMPLETED
                            || record.replaySnapshot() == null
                            || !UsernameRecoveryCancelReplayCodec.decode(record.replaySnapshot()).decoded()) {
                        return Mono.error(UsernameRecoveryCancelException.systemFailure());
                    }
                    return Mono.just(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));
                });
    }

    private boolean originRefererAllowed(String origin, String referer) {
        return allowedOrigins.isEmpty() || originRefererValidator.validate(origin, referer, allowedOrigins).valid();
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
