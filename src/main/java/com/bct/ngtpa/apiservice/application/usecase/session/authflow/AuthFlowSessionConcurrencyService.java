package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import java.util.Objects;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

public class AuthFlowSessionConcurrencyService {
    private final AuthFlowSessionLifecycleService lifecycleService;

    public AuthFlowSessionConcurrencyService(AuthFlowSessionLifecycleService lifecycleService) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
    }

    public Mono<StartAuthFlowSessionWithConcurrencyResult> start(StartAuthFlowSessionWithConcurrencyCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.concurrencyPolicy().mode() != SessionConcurrencyMode.SINGLE_ACTIVE_FLOW
                || command.concurrencyPolicy().onNewFlow() != SessionOnNewFlowPolicy.REPLACE_EXISTING) {
            return Mono.error(new UnsupportedOperationException("Unsupported auth flow concurrency policy"));
        }

        if (command.existingAuthFlowSessionId() == null) {
            return startNew(command, AuthFlowConcurrencyDecision.CREATED_NEW);
        }

        return lifecycleService.load(new LoadAuthFlowSessionCommand(command.existingAuthFlowSessionId(), command.correlationId()))
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return startNew(command, AuthFlowConcurrencyDecision.CREATED_NEW_EXISTING_MISSING);
                    }
                    AuthFlowSession existing = optional.get();
                    if (existing.isTerminal()) {
                        return startNew(command, AuthFlowConcurrencyDecision.CREATED_NEW_EXISTING_ALREADY_TERMINAL);
                    }
                    return cancelForReplacement(existing, command)
                            .then(startNew(command, AuthFlowConcurrencyDecision.REPLACED_EXISTING));
                });
    }


    private Mono<AuthFlowSession> cancelForReplacement(
            AuthFlowSession existing,
            StartAuthFlowSessionWithConcurrencyCommand command) {
        return Mono.defer(() -> lifecycleService.cancel(new CancelAuthFlowSessionCommand(
                        existing.authFlowSessionId(),
                        "REPLACE_EXISTING",
                        command.correlationId())))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .filter(this::revisionConflict));
    }

    private boolean revisionConflict(Throwable error) {
        return error instanceof IllegalStateException
                && "Auth flow session revision conflict".equals(error.getMessage());
    }

    private Mono<StartAuthFlowSessionWithConcurrencyResult> startNew(
            StartAuthFlowSessionWithConcurrencyCommand command,
            AuthFlowConcurrencyDecision decision) {
        var startCommand = new StartAuthFlowSessionCommand(
                command.scenario(),
                command.initialStatus(),
                command.ttlPolicy(),
                command.sessionIdPrefix(),
                command.sessionIdEntropyBytes(),
                command.data(),
                command.csrfTokenHash(),
                command.activeOtpChallengeId(),
                command.correlationId());
        return lifecycleService.start(startCommand)
                .map(result -> new StartAuthFlowSessionWithConcurrencyResult(
                        result.session(),
                        result.expiresInSeconds(),
                        decision));
    }
}
