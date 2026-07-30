package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
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

public class DefaultForgotPasswordProtectedAuthFlowContextLoader
        implements ForgotPasswordProtectedAuthFlowContextLoader {

    private final AuthFlowSessionLifecycleService lifecycleService;
    private final AuthFlowSessionCsrfService csrfService;
    private final ForgotPasswordAuthFlowContextProtectorPort contextProtector;
    private final Clock clock;

    public DefaultForgotPasswordProtectedAuthFlowContextLoader(
            AuthFlowSessionLifecycleService lifecycleService,
            AuthFlowSessionCsrfService csrfService,
            ForgotPasswordAuthFlowContextProtectorPort contextProtector,
            Clock clock) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.csrfService = Objects.requireNonNull(csrfService, "csrfService must not be null");
        this.contextProtector = Objects.requireNonNull(contextProtector, "contextProtector must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<ForgotPasswordProtectedAuthFlowContextResult> load(
            ForgotPasswordProtectedAuthFlowContextCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);
        Mono<ForgotPasswordProtectedAuthFlowContextResult> pipeline = lifecycleService
                .load(new LoadAuthFlowSessionCommand(command.authFlowSessionId(), command.correlationId()))
                .map(optional -> validateAndDecrypt(optional, now, command));

        if (!command.csrfRequired()) {
            return pipeline;
        }

        return csrfService.validateForSession(new ValidateAuthFlowSessionCsrfCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                now,
                command.origin(),
                command.referer(),
                java.util.List.of(),
                command.correlationId()))
                .flatMap(validation -> {
                    if (!validation.valid()) {
                        return Mono
                                .just(ForgotPasswordProtectedAuthFlowContextResult.invalid(map(validation.status())));
                    }
                    return pipeline;
                })
                .onErrorResume(RuntimeException.class,
                        ignored -> Mono.just(ForgotPasswordProtectedAuthFlowContextResult.invalid(
                                ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID)));
    }

    private ForgotPasswordProtectedAuthFlowContextResult validateAndDecrypt(
            Optional<AuthFlowSession> optional,
            Instant now,
            ForgotPasswordProtectedAuthFlowContextCommand command) {
        if (optional.isEmpty()) {
            return ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.MISSING_SESSION);
        }
        AuthFlowSession session = optional.get();
        if (session.isExpired(now) || session.isTerminal()) {
            return ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION);
        }
        if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD) {
            return ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.WRONG_SCENARIO);
        }
        if (!command.acceptedStatuses().contains(session.status())) {
            return ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.INVALID_STATUS);
        }
        Map<String, String> attributes = attributes(session.data());
        String ciphertext = attributes.get(ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT);
        String keyVersion = attributes.get(ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION);
        String algorithm = attributes.get(ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM);
        if (isBlank(ciphertext) || isBlank(keyVersion)) {
            return ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.MISSING_CONTEXT);
        }

        try {
            var context = contextProtector.unprotect(new ForgotPasswordAuthFlowEncryptedContext(
                    ciphertext,
                    keyVersion,
                    isBlank(algorithm) ? ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM : algorithm));
            return ForgotPasswordProtectedAuthFlowContextResult.valid(session.activeOtpChallengeId(), context);
        } catch (RuntimeException exception) {
            return ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.CONTEXT_DECRYPTION_FAILED);
        }
    }

    private ForgotPasswordProtectedAuthFlowContextStatus map(AuthFlowSessionCsrfValidationStatus status) {
        if (status == AuthFlowSessionCsrfValidationStatus.SESSION_MISSING) {
            return ForgotPasswordProtectedAuthFlowContextStatus.MISSING_SESSION;
        }
        if (status == AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED
                || status == AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE) {
            return ForgotPasswordProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION;
        }
        if (status == AuthFlowSessionCsrfValidationStatus.ORIGIN_REFERER_INVALID) {
            return ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID;
        }
        return ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID;
    }

    private Map<String, String> attributes(AuthFlowSessionData data) {
        return data == null || data.attributes() == null ? Map.of() : data.attributes();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}