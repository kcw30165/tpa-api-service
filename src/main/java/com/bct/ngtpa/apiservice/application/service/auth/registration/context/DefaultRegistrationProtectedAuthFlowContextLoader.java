package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfValidationStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.LoadAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.ValidateAuthFlowSessionCsrfCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Default protected context loader for registration OTP resend/verify use
 * cases.
 *
 * <p>
 * This class intentionally lives in the application service layer and remains
 * framework-free. It validates AuthFlowSession/CSRF state, ensures the flow is
 * the
 * registration OTP step, decrypts the encrypted registration context from
 * session
 * attributes, and returns only application DTOs.
 * </p>
 */
public class DefaultRegistrationProtectedAuthFlowContextLoader implements RegistrationProtectedAuthFlowContextLoader {
    private final AuthFlowSessionLifecycleService lifecycleService;
    private final AuthFlowSessionCsrfService csrfService;
    private final RegistrationAuthFlowContextProtectorPort contextProtector;
    private final Clock clock;

    public DefaultRegistrationProtectedAuthFlowContextLoader(
            AuthFlowSessionLifecycleService lifecycleService,
            AuthFlowSessionCsrfService csrfService,
            RegistrationAuthFlowContextProtectorPort contextProtector,
            Clock clock) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.csrfService = Objects.requireNonNull(csrfService, "csrfService must not be null");
        this.contextProtector = Objects.requireNonNull(contextProtector, "contextProtector must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<RegistrationProtectedAuthFlowContextResult> load(RegistrationProtectedAuthFlowContextCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);
        return csrfService.validateForSession(new ValidateAuthFlowSessionCsrfCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                now,
                command.origin(),
                command.referer(),
                java.util.List.of(),
                command.correlationId()))
                .flatMap(csrf -> {
                    if (!csrf.valid()) {
                        return Mono.just(RegistrationProtectedAuthFlowContextResult.invalid(toStatus(csrf.status())));
                    }
                    return lifecycleService
                            .load(new LoadAuthFlowSessionCommand(command.authFlowSessionId(), command.correlationId()))
                            .map(optional -> validateAndDecrypt(optional, now, command.acceptedStatuses()));
                })
                .onErrorResume(RuntimeException.class,
                        ignored -> Mono.just(RegistrationProtectedAuthFlowContextResult.invalid(
                                RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID)));
    }

    private RegistrationProtectedAuthFlowContextResult validateAndDecrypt(
            Optional<AuthFlowSession> optional,
            Instant now,
            java.util.Set<AuthFlowStatus> acceptedStatuses) {
        if (optional.isEmpty()) {
            return RegistrationProtectedAuthFlowContextResult
                    .invalid(RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION);
        }
        AuthFlowSession session = optional.get();
        if (session.isExpired(now) || session.isTerminal()) {
            return RegistrationProtectedAuthFlowContextResult
                    .invalid(RegistrationProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION);
        }
        if (session.scenario() != AuthFlowScenario.REGISTRATION) {
            return RegistrationProtectedAuthFlowContextResult
                    .invalid(RegistrationProtectedAuthFlowContextStatus.WRONG_SCENARIO);
        }
        if (!acceptedStatuses.contains(session.status())) {
            return RegistrationProtectedAuthFlowContextResult
                    .invalid(RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS);
        }
        if (isBlank(session.activeOtpChallengeId())) {
            return RegistrationProtectedAuthFlowContextResult
                    .invalid(RegistrationProtectedAuthFlowContextStatus.MISSING_ACTIVE_OTP);
        }

        Map<String, String> attributes = attributes(session.data());
        String ciphertext = attributes.get(RegistrationAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT);
        String keyVersion = attributes.get(RegistrationAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION);
        String algorithm = attributes.get(RegistrationAuthFlowContextAttributeNames.CONTEXT_ALGORITHM);
        if (isBlank(ciphertext) || isBlank(keyVersion)) {
            return RegistrationProtectedAuthFlowContextResult
                    .invalid(RegistrationProtectedAuthFlowContextStatus.MISSING_CONTEXT);
        }

        try {
            RegistrationAuthFlowContext context = contextProtector.unprotect(
                    new RegistrationAuthFlowEncryptedContext(ciphertext, keyVersion, algorithm));
            return RegistrationProtectedAuthFlowContextResult.valid(
                    session.activeOtpChallengeId(),
                    session.data() == null ? null : session.data().maskedDestination(),
                    context);
        } catch (RuntimeException exception) {
            return RegistrationProtectedAuthFlowContextResult.invalid(
                    RegistrationProtectedAuthFlowContextStatus.CONTEXT_DECRYPTION_FAILED);
        }
    }

    private RegistrationProtectedAuthFlowContextStatus toStatus(AuthFlowSessionCsrfValidationStatus status) {
        if (status == AuthFlowSessionCsrfValidationStatus.SESSION_MISSING) {
            return RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION;
        }
        if (status == AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED
                || status == AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE) {
            return RegistrationProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION;
        }
        return RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID;
    }

    private Map<String, String> attributes(AuthFlowSessionData data) {
        return data == null || data.attributes() == null ? Map.of() : data.attributes();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
