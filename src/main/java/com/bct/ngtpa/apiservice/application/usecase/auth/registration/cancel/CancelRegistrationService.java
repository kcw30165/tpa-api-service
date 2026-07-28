package com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallengeInvalidationReason;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCancellationReason;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.ValidateAuthFlowSessionCsrfCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class CancelRegistrationService implements CancelRegistrationUseCase {
    private static final String SOURCE_SERVER = "SERVER";
    private static final String TARGET_PAGE = "PAGE";
    private static final String ERROR_AUTH_FLOW_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID
            .value();
    private static final String ERROR_CSRF_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.CSRF_INVALID
            .value();
    private static final String ERROR_CANCEL_UNAVAILABLE = com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE
            .value();

    private final AuthFlowSessionPort sessions;
    private final AuthFlowSessionCsrfService csrfService;
    private final OtpChallengePort otpChallenges;
    private final AuthFlowSessionCancellationPort cancellationPort;
    private final Clock clock;

    public CancelRegistrationService(
            AuthFlowSessionPort sessions,
            AuthFlowSessionCsrfService csrfService,
            OtpChallengePort otpChallenges,
            AuthFlowSessionCancellationPort cancellationPort,
            Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.csrfService = Objects.requireNonNull(csrfService, "csrfService must not be null");
        this.otpChallenges = Objects.requireNonNull(otpChallenges, "otpChallenges must not be null");
        this.cancellationPort = Objects.requireNonNull(cancellationPort, "cancellationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<CancelRegistrationResult> cancel(CancelRegistrationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        return sessions.load(command.authFlowSessionId())
                .flatMap(optional -> optional
                        .map(session -> cancelExisting(command, session, now))
                        .orElseGet(() -> Mono.just(cleanup(
                                CancelRegistrationResultStatus.SESSION_NOT_FOUND))))
                .onErrorResume(RuntimeException.class, ignored -> Mono.just(systemFailure()));
    }

    private Mono<CancelRegistrationResult> cancelExisting(
            CancelRegistrationCommand command,
            AuthFlowSession session,
            Instant now) {
        if (session.scenario() != AuthFlowScenario.REGISTRATION) {
            return Mono.just(new CancelRegistrationResult(
                    CancelRegistrationResultStatus.INVALID_AUTH_FLOW,
                    false,
                    false,
                    List.of(error(WorkflowErrorType.BUSINESS, ERROR_AUTH_FLOW_INVALID))));
        }
        if (session.status() == AuthFlowStatus.CANCELLED) {
            return Mono.just(cleanup(CancelRegistrationResultStatus.ALREADY_CANCELLED));
        }
        if (session.status() == AuthFlowStatus.COMPLETED
                || session.status() == AuthFlowStatus.EXPIRED
                || session.isExpired(now)) {
            return Mono.just(cleanup(CancelRegistrationResultStatus.ALREADY_TERMINAL));
        }
        if (command.csrfToken() == null || command.csrfToken().isBlank()) {
            return Mono.just(csrfFailure());
        }
        return csrfService.validateForSession(new ValidateAuthFlowSessionCsrfCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                now,
                command.origin(),
                command.referer(),
                List.of(),
                command.correlationId()))
                .flatMap(csrf -> {
                    if (!csrf.valid()) {
                        return Mono.just(csrfFailure());
                    }
                    return invalidateOtpIfPresent(command, session, now)
                            .then(cancellationPort.cancel(
                                    session.authFlowSessionId(),
                                    AuthFlowSessionCancellationReason.USER_CANCELLED,
                                    now))
                            .map(result -> mapCancellationStatus(result.status()));
                })
                .onErrorResume(RuntimeException.class, ignored -> Mono.just(systemFailure()));
    }

    private Mono<Void> invalidateOtpIfPresent(
            CancelRegistrationCommand command,
            AuthFlowSession session,
            Instant now) {
        if (session.activeOtpChallengeId() == null || session.activeOtpChallengeId().isBlank()) {
            return Mono.empty();
        }
        return otpChallenges.invalidateChallenge(new InvalidateOtpChallengeCommand(
                session.activeOtpChallengeId(),
                OtpChallengeInvalidationReason.USER_CANCELLED_REGISTRATION,
                now));
    }

    private CancelRegistrationResult mapCancellationStatus(SessionCancellationStatus status) {
        return switch (status) {
            case CANCELLED -> cleanup(CancelRegistrationResultStatus.CANCELLED);
            case ALREADY_CANCELLED -> cleanup(CancelRegistrationResultStatus.ALREADY_CANCELLED);
            case ALREADY_TERMINAL -> cleanup(CancelRegistrationResultStatus.ALREADY_TERMINAL);
            case NOT_FOUND -> cleanup(CancelRegistrationResultStatus.SESSION_NOT_FOUND);
        };
    }

    private CancelRegistrationResult cleanup(CancelRegistrationResultStatus status) {
        return new CancelRegistrationResult(status, true, true, List.of());
    }

    private CancelRegistrationResult csrfFailure() {
        return new CancelRegistrationResult(
                CancelRegistrationResultStatus.CSRF_INVALID,
                false,
                false,
                List.of(error(WorkflowErrorType.BUSINESS, ERROR_CSRF_INVALID)));
    }

    private CancelRegistrationResult systemFailure() {
        return new CancelRegistrationResult(
                CancelRegistrationResultStatus.SYSTEM_FAILED,
                false,
                false,
                List.of(error(WorkflowErrorType.SYSTEM, ERROR_CANCEL_UNAVAILABLE)));
    }

    private WorkflowErrorDescriptor error(WorkflowErrorType type, String code) {
        return new WorkflowErrorDescriptor(
                type,
                code,
                null,
                List.of(TARGET_PAGE),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER);
    }
}
