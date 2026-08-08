package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Application-layer integration that safely loads and reserves a protected Username Recovery
 * session candidate through the committed {@link UsernameRecoverySessionPort}.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * sections 2-4 (candidate reservation precedes sender acceptance; revision persisted consistently)
 * and {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} sections 7-8
 * (UR-REQ-001 missing/invalid/expired session, UR-REQ-002 CSRF, UR-REQ-003 Scheme-Ref,
 * UR-REQ-004 registration, UR-REQ-008 already requested, UR-REQ-009 completed workflow, and the
 * distinct revision-conflict and system-failure mappings).
 *
 * <p>Behavioral contract:
 *
 * <ul>
 *   <li>Loads the session by typed {@code UsernameRecoverySessionId} through the committed port
 *       only; an absent record maps to {@link UsernameRecoveryReservationOutcome#SESSION_ABSENT},
 *       an expired record to {@link UsernameRecoveryReservationOutcome#SESSION_EXPIRED}, and
 *       infrastructure errors to {@link UsernameRecoveryReservationOutcome#SYSTEM_FAILED}.</li>
 *   <li>Verifies the presented raw CSRF token against the stored hash through the established
 *       constant-time shared abstraction ({@link CsrfTokenValidationService}); the raw token is
 *       accepted only at the {@link #reserve} method boundary and is never stored in a record,
 *       field, log, exception, or {@code toString()}.</li>
 *   <li>Resolves the opaque Scheme-Ref only against candidates inside the loaded session and
 *       rejects fabricated/cross-session refs, unregistered, already-requested, and otherwise
 *       ineligible candidates with typed outcomes before any mutation.</li>
 *   <li>Reserves an eligible registered candidate through the Task 010 aggregate's pure
 *       {@code reserve} transition and persists the transition with expected-revision
 *       compare-and-set semantics; revision conflicts map distinctly and are never auto-retried.</li>
 * </ul>
 */
public class UsernameRecoveryReservationService {

    private final UsernameRecoverySessionPort sessionPort;
    private final CsrfTokenValidationService csrfTokenValidationService;

    public UsernameRecoveryReservationService(
            UsernameRecoverySessionPort sessionPort,
            CsrfTokenValidationService csrfTokenValidationService) {
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort must not be null");
        this.csrfTokenValidationService =
                Objects.requireNonNull(csrfTokenValidationService,
                        "csrfTokenValidationService must not be null");
    }

    /**
     * Loads the typed session, verifies expiry and CSRF, resolves Scheme-Ref ownership and
     * eligibility, reserves through the aggregate, and persists with expected-revision CAS.
     *
     * <p>The raw CSRF token is a method-boundary parameter only: it is passed directly to the
     * committed constant-time validation abstraction and is never retained.
     */
    public Mono<UsernameRecoveryReservationResult> reserve(
            UsernameRecoveryReservationCommand command, String rawCsrfToken) {
        Objects.requireNonNull(command, "command must not be null");
        return sessionPort.load(command.sessionId())
                .onErrorMap(error -> new UsernameRecoverySessionPersistenceException(error))
                .flatMap(loadResult -> handleLoad(command, rawCsrfToken, loadResult))
                .onErrorResume(UsernameRecoverySessionPersistenceException.class,
                        error -> Mono.just(UsernameRecoveryReservationResult.systemFailed()));
    }

    private Mono<UsernameRecoveryReservationResult> handleLoad(
            UsernameRecoveryReservationCommand command,
            String rawCsrfToken,
            UsernameRecoverySessionLoadResult loadResult) {
        return switch (loadResult.outcome()) {
            case LOADED -> validateLoaded(command, rawCsrfToken, loadResult.session());
            case SESSION_NOT_FOUND -> Mono.just(UsernameRecoveryReservationResult.absent());
            case SYSTEM_FAILURE -> Mono.just(UsernameRecoveryReservationResult.systemFailed());
            default -> throw new IllegalStateException(
                    "unexpected session load outcome: " + loadResult.outcome());
        };
    }

    private Mono<UsernameRecoveryReservationResult> validateLoaded(
            UsernameRecoveryReservationCommand command,
            String rawCsrfToken,
            UsernameRecoverySession session) {
        if (!session.expiresAt().isAfter(command.now())) {
            return Mono.just(UsernameRecoveryReservationResult.expired());
        }
        if (session.isTerminal()) {
            return Mono.just(UsernameRecoveryReservationResult.terminal());
        }
        Optional<UsernameRecoveryReservationResult> csrf = verifyCsrf(rawCsrfToken, session);
        if (csrf.isPresent()) {
            return Mono.just(csrf.get());
        }
        Optional<UsernameRecoveryReservationResult> eligibility =
                checkEligibility(session, command.schemeRef());
        if (eligibility.isPresent()) {
            return Mono.just(eligibility.get());
        }
        UsernameRecoverySession reserved = session.reserve(command.schemeRef());
        return persistReservation(session, reserved);
    }

    private Optional<UsernameRecoveryReservationResult> verifyCsrf(
            String rawCsrfToken, UsernameRecoverySession session) {
        CsrfTokenValidationResult validation;
        try {
            validation = csrfTokenValidationService.validate(
                    rawCsrfToken, session.csrfHash().value());
        } catch (RuntimeException exception) {
            return Optional.of(UsernameRecoveryReservationResult.systemFailed());
        }
        if (!validation.valid()) {
            return Optional.of(UsernameRecoveryReservationResult.csrfInvalid());
        }
        return Optional.empty();
    }

    private Optional<UsernameRecoveryReservationResult> checkEligibility(
            UsernameRecoverySession session, UsernameRecoverySchemeRef schemeRef) {
        UsernameRecoverySessionCandidate candidate = session.candidates().stream()
                .filter(c -> c.schemeRef().equals(schemeRef))
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            return Optional.of(UsernameRecoveryReservationResult.schemeRefInvalid());
        }
        if (!candidate.registered()) {
            return Optional.of(UsernameRecoveryReservationResult.registrationRequired());
        }
        if (candidate.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED) {
            return Optional.of(UsernameRecoveryReservationResult.alreadyRequested());
        }
        if (candidate.deliveryState() != UsernameRecoveryDeliveryState.AVAILABLE) {
            return Optional.of(UsernameRecoveryReservationResult.candidateNotAvailable());
        }
        return Optional.empty();
    }

    private Mono<UsernameRecoveryReservationResult> persistReservation(
            UsernameRecoverySession loaded, UsernameRecoverySession reserved) {
        UsernameRecoverySessionMutationCommand mutation = new UsernameRecoverySessionMutationCommand(
                loaded.sessionId(), loaded.revision(), reserved);
        return sessionPort.update(mutation)
                .onErrorMap(error -> new UsernameRecoverySessionPersistenceException(error))
                .map(result -> switch (result.outcome()) {
                    case APPLIED -> UsernameRecoveryReservationResult.reserved();
                    case SESSION_NOT_FOUND -> UsernameRecoveryReservationResult.absent();
                    case REVISION_CONFLICT -> UsernameRecoveryReservationResult.revisionConflict();
                    case SYSTEM_FAILURE -> UsernameRecoveryReservationResult.systemFailed();
                    default -> throw new IllegalStateException(
                            "unexpected session update outcome: " + result.outcome());
                });
    }
    private static final class UsernameRecoverySessionPersistenceException
            extends RuntimeException {
        private UsernameRecoverySessionPersistenceException(Throwable cause) {
            super("Username recovery session persistence failed", cause);
        }
    }
}
