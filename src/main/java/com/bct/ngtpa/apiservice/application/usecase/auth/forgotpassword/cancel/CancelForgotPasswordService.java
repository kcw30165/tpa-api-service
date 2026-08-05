package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallengeInvalidationReason;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCancellationReason;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.port.in.CancelForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidationStatus;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.ValidateAuthFlowSessionCsrfCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Forgot Password cancellation through the shared revision-aware cancellation boundary. */
public final class CancelForgotPasswordService implements CancelForgotPasswordUseCase {
    private static final String SOURCE_SERVER = "SERVER";
    private static final String TARGET_PAGE = "PAGE";
    private static final String ERROR_AUTH_FLOW_INVALID = ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value();
    private static final String ERROR_CSRF_INVALID = ForgotPasswordErrorKey.CSRF_INVALID.value();
    private static final String ERROR_SYSTEM_UNAVAILABLE = ForgotPasswordErrorKey.SYSTEM_UNAVAILABLE.value();

    private final AuthFlowSessionPort sessions;
    private final AuthFlowSessionCsrfService csrfService;
    private final OtpChallengePort otpChallenges;
    private final AuthFlowSessionCancellationPort cancellationPort;
    private final Clock clock;
    private final List<String> allowedOrigins;
    private final OriginRefererValidator originRefererValidator = new OriginRefererValidator();

    public CancelForgotPasswordService(
            AuthFlowSessionPort sessions,
            AuthFlowSessionCsrfService csrfService,
            OtpChallengePort otpChallenges,
            AuthFlowSessionCancellationPort cancellationPort,
            Clock clock,
            List<String> allowedOrigins) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.csrfService = Objects.requireNonNull(csrfService, "csrfService must not be null");
        this.otpChallenges = Objects.requireNonNull(otpChallenges, "otpChallenges must not be null");
        this.cancellationPort = Objects.requireNonNull(cancellationPort, "cancellationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    @Override
    public Mono<CancelForgotPasswordResult> cancel(CancelForgotPasswordCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        return sessions.load(command.authFlowSessionId())
                .flatMap(optional -> optional
                        .map(session -> cancelExisting(command, session, now))
                        .orElseGet(() -> Mono.just(
                                cleanup(CancelForgotPasswordStatus.SESSION_NOT_FOUND))))
                .onErrorResume(RuntimeException.class, ignored -> Mono.just(systemFailure()));
    }

    private Mono<CancelForgotPasswordResult> cancelExisting(
            CancelForgotPasswordCommand command,
            AuthFlowSession session,
            Instant now) {
        if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD) {
            return Mono.just(validationFailure(
                    CancelForgotPasswordStatus.INVALID_AUTH_FLOW,
                    WorkflowErrorType.FORM,
                    ERROR_AUTH_FLOW_INVALID));
        }
        if (session.status() == AuthFlowStatus.CANCELLED) {
            return Mono.just(cleanup(CancelForgotPasswordStatus.ALREADY_CANCELLED));
        }
        if (session.status() == AuthFlowStatus.COMPLETED
                || session.status() == AuthFlowStatus.EXPIRED
                || session.status() == AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED
                || session.isExpired(now)) {
            return Mono.just(cleanup(CancelForgotPasswordStatus.ALREADY_TERMINAL));
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
                    if (!originAllowed(command.origin(), command.referer())) {
                        return Mono.just(csrfFailure());
                    }
                    return invalidateOtpIfPresent(session, now)
                            .then(cancellationPort.cancel(
                                    session.authFlowSessionId(),
                                    AuthFlowSessionCancellationReason.USER_CANCELLED,
                                    now))
                            .map(result -> mapCancellationStatus(result.status()));
                })
                .onErrorResume(RuntimeException.class, ignored -> Mono.just(systemFailure()));
    }

    private boolean originAllowed(String origin, String referer) {
        if (allowedOrigins.isEmpty()) {
            return true;
        }
        return originRefererValidator
                .validate(origin, referer, allowedOrigins).status() == OriginRefererValidationStatus.VALID;
    }

    private Mono<Void> invalidateOtpIfPresent(AuthFlowSession session, Instant now) {
        if (session.activeOtpChallengeId() == null || session.activeOtpChallengeId().isBlank()) {
            return Mono.empty();
        }
        return otpChallenges.invalidateChallenge(new InvalidateOtpChallengeCommand(
                session.activeOtpChallengeId(),
                OtpChallengeInvalidationReason.USER_CANCELLED_FORGOT_PASSWORD,
                now));
    }

    private CancelForgotPasswordResult mapCancellationStatus(SessionCancellationStatus status) {
        return switch (status) {
            case CANCELLED -> cleanup(CancelForgotPasswordStatus.CANCELLED);
            case ALREADY_CANCELLED -> cleanup(CancelForgotPasswordStatus.ALREADY_CANCELLED);
            case ALREADY_TERMINAL -> cleanup(CancelForgotPasswordStatus.ALREADY_TERMINAL);
            case NOT_FOUND -> cleanup(CancelForgotPasswordStatus.SESSION_NOT_FOUND);
        };
    }

    private CancelForgotPasswordResult cleanup(CancelForgotPasswordStatus status) {
        return new CancelForgotPasswordResult(status, true, true, List.of());
    }

    private CancelForgotPasswordResult csrfFailure() {
        return validationFailure(
                CancelForgotPasswordStatus.CSRF_INVALID,
                WorkflowErrorType.FORM,
                ERROR_CSRF_INVALID);
    }

    private CancelForgotPasswordResult systemFailure() {
        return validationFailure(
                CancelForgotPasswordStatus.SYSTEM_FAILED,
                WorkflowErrorType.SYSTEM,
                ERROR_SYSTEM_UNAVAILABLE);
    }

    private CancelForgotPasswordResult validationFailure(
            CancelForgotPasswordStatus status,
            WorkflowErrorType type,
            String code) {
        return new CancelForgotPasswordResult(
                status,
                false,
                false,
                List.of(new WorkflowErrorDescriptor(
                        type,
                        code,
                        null,
                        List.of(TARGET_PAGE),
                        WorkflowErrorSeverity.ERROR,
                        SOURCE_SERVER)));
    }
}