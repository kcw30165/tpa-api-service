package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Model B reservation boundary for the Username Recovery workflow-state
 * sidecar.
 */
@Slf4j
public final class UsernameRecoveryReservationService {
    private final AuthFlowSessionPort authFlowSessionPort;
    private final UsernameRecoveryWorkflowStatePort workflowStatePort;
    private final CsrfTokenValidationService csrfTokenValidationService;

    public UsernameRecoveryReservationService(
            AuthFlowSessionPort authFlowSessionPort,
            UsernameRecoveryWorkflowStatePort workflowStatePort,
            CsrfTokenValidationService csrfTokenValidationService) {
        this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort,
                "authFlowSessionPort must not be null");
        this.workflowStatePort = Objects.requireNonNull(workflowStatePort,
                "workflowStatePort must not be null");
        this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService,
                "csrfTokenValidationService must not be null");
    }

    public Mono<UsernameRecoveryReservationResult> reserve(
            UsernameRecoveryReservationCommand command, String rawCsrfToken) {
        Objects.requireNonNull(command, "command must not be null");
        return authFlowSessionPort.loadSnapshot(command.authFlowSessionId())
                .onErrorMap(RuntimeException.class, ReservationPersistenceException::new)
                .flatMap(optional -> optional
                        .map(snapshot -> validateEnvelope(command, rawCsrfToken, snapshot))
                        .orElseGet(() -> Mono.just(UsernameRecoveryReservationResult.absent())))
                .doOnError(
                        ReservationPersistenceException.class,
                        error -> log.error(
                                "event=username-recovery.reservation.persistence-failed errorType={} causeType={}",
                                error.getClass().getSimpleName(),
                                error.getCause() == null
                                        ? "none"
                                        : error.getCause().getClass().getSimpleName(),
                                error))
                .onErrorResume(
                        ReservationPersistenceException.class,
                        error -> Mono.just(UsernameRecoveryReservationResult.systemFailed()));
    }

    private Mono<UsernameRecoveryReservationResult> validateEnvelope(
            UsernameRecoveryReservationCommand command,
            String rawCsrfToken,
            AuthFlowSessionSnapshot snapshot) {
        AuthFlowSession envelope = snapshot.session();
        if (envelope.scenario() != AuthFlowScenario.USERNAME_RECOVERY) {
            return Mono.just(UsernameRecoveryReservationResult.absent());
        }
        if (envelope.isExpired(command.now())) {
            return Mono.just(UsernameRecoveryReservationResult.expired());
        }
        if (envelope.isTerminal() || envelope.status() != AuthFlowStatus.STARTED) {
            return Mono.just(UsernameRecoveryReservationResult.terminal());
        }
        Optional<UsernameRecoveryReservationResult> csrf = verifyCsrf(
                rawCsrfToken, envelope.csrfTokenHash());
        if (csrf.isPresent()) {
            return Mono.just(csrf.get());
        }
        return workflowStatePort.load(command.authFlowSessionId())
                .doOnNext(loadResult -> log.debug(
                        "event=username-recovery.reservation.workflow-load outcome={}",
                        loadResult.outcome()))
                .onErrorMap(RuntimeException.class, ReservationPersistenceException::new)
                .flatMap(loadResult -> handleWorkflowLoad(command, loadResult));
    }

    private Optional<UsernameRecoveryReservationResult> verifyCsrf(
            String rawCsrfToken, String storedHash) {
        try {
            CsrfTokenValidationResult result =
                    csrfTokenValidationService.validate(rawCsrfToken, storedHash);
            log.debug(
                    "event=username-recovery.csrf.validation-completed valid={}",
                    result.valid());
            return result.valid() ? Optional.empty()
                    : Optional.of(UsernameRecoveryReservationResult.csrfInvalid());
        } catch (RuntimeException exception) {
            log.error(
                    "event=username-recovery.csrf.validation-failed errorType={}",
                    exception.getClass().getSimpleName(),
                    exception);
            return Optional.of(UsernameRecoveryReservationResult.systemFailed());
        }
    }

    private Mono<UsernameRecoveryReservationResult> handleWorkflowLoad(
            UsernameRecoveryReservationCommand command,
            UsernameRecoveryWorkflowStateLoadResult loadResult) {
        return switch (loadResult.outcome()) {
            case LOADED -> validateWorkflow(command, loadResult.state());
            case STATE_NOT_FOUND -> Mono.just(UsernameRecoveryReservationResult.absent());
            case SYSTEM_FAILURE -> Mono.just(UsernameRecoveryReservationResult.systemFailed());
            default -> throw new IllegalStateException(
                    "unexpected workflow-state load outcome: " + loadResult.outcome());
        };
    }

    private Mono<UsernameRecoveryReservationResult> validateWorkflow(
            UsernameRecoveryReservationCommand command,
            UsernameRecoveryWorkflowState state) {
        if (!command.authFlowSessionId().equals(state.authFlowSessionId())) {
            return Mono.just(UsernameRecoveryReservationResult.systemFailed());
        }
        if (state.status() != UsernameRecoveryWorkflowStatus.ACTIVE
                && state.status() != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED) {
            return Mono.just(UsernameRecoveryReservationResult.terminal());
        }
        Optional<UsernameRecoveryReservationResult> eligibility = checkEligibility(state, command.schemeRef());
        if (eligibility.isPresent()) {
            return Mono.just(eligibility.get());
        }
        UsernameRecoveryWorkflowState reserved = state.reserve(command.schemeRef());
        return persistReservation(state, reserved);
    }

    private Optional<UsernameRecoveryReservationResult> checkEligibility(
            UsernameRecoveryWorkflowState state, UsernameRecoverySchemeRef schemeRef) {
        UsernameRecoverySessionCandidate candidate = state.candidates().stream()
                .filter(value -> value.schemeRef().equals(schemeRef))
                .findFirst().orElse(null);
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
            UsernameRecoveryWorkflowState loaded,
            UsernameRecoveryWorkflowState reserved) {
        UsernameRecoveryWorkflowStateMutationCommand mutation = new UsernameRecoveryWorkflowStateMutationCommand(
                loaded.authFlowSessionId(), loaded.revision(), reserved);
        return workflowStatePort.update(mutation)
                .doOnNext(result -> log.debug(
                        "event=username-recovery.reservation.workflow-update outcome={} expectedRevision={}",
                        result.outcome(),
                        loaded.revision().value()))
                .onErrorMap(RuntimeException.class, ReservationPersistenceException::new)
                .map(this::mapMutation);
    }

    private UsernameRecoveryReservationResult mapMutation(
            UsernameRecoveryWorkflowStateMutationResult result) {
        return switch (result.outcome()) {
            case APPLIED -> UsernameRecoveryReservationResult.reserved();
            case STATE_NOT_FOUND -> UsernameRecoveryReservationResult.absent();
            case REVISION_CONFLICT -> UsernameRecoveryReservationResult.revisionConflict();
            case SYSTEM_FAILURE -> UsernameRecoveryReservationResult.systemFailed();
            default -> throw new IllegalStateException(
                    "unexpected workflow-state update outcome: " + result.outcome());
        };
    }

    private static final class ReservationPersistenceException extends RuntimeException {
        private ReservationPersistenceException(Throwable cause) {
            super("Username recovery reservation persistence failed", cause);
        }
    }
}
