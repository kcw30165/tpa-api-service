package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

public class AuthFlowSessionLifecycleService {
    private final AuthFlowSessionPort authFlowSessionPort;
    private final SessionIdGenerator sessionIdGenerator;
    private final SessionTtlCalculator sessionTtlCalculator;
    private final Clock clock;

    public AuthFlowSessionLifecycleService(AuthFlowSessionPort authFlowSessionPort, SessionIdGenerator sessionIdGenerator, SessionTtlCalculator sessionTtlCalculator, Clock clock) {
        this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
        this.sessionIdGenerator = Objects.requireNonNull(sessionIdGenerator, "sessionIdGenerator must not be null");
        this.sessionTtlCalculator = Objects.requireNonNull(sessionTtlCalculator, "sessionTtlCalculator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Mono<StartAuthFlowSessionResult> start(StartAuthFlowSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!command.ttlPolicy().enabled()) {
            return Mono.error(new IllegalArgumentException("Auth flow TTL policy must be enabled"));
        }
        return Mono.defer(() -> {
            Instant now = Instant.now(clock);
            var sessionId = sessionIdGenerator.generate(new SessionIdGenerationCommand(command.sessionIdPrefix(), command.sessionIdEntropyBytes()));
            var authFlowSessionId = new AuthFlowSessionId(sessionId.value());
            Instant expiresAt = sessionTtlCalculator.expiresAt(now, command.ttlPolicy());
            var createCommand = new AuthFlowSessionCreateCommand(authFlowSessionId, command.scenario(), command.initialStatus(), now, expiresAt, command.data(), command.csrfTokenHash(), command.activeOtpChallengeId());
            return authFlowSessionPort.create(createCommand)
                    .map(session -> new StartAuthFlowSessionResult(session, command.ttlPolicy().ttlSeconds()));
        });
    }

    public Mono<Optional<AuthFlowSession>> load(LoadAuthFlowSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return authFlowSessionPort.load(command.authFlowSessionId());
    }

    public Mono<AuthFlowSession> updateStatus(UpdateAuthFlowStatusCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return mutateUsable(command.authFlowSessionId(), session -> copy(session, command.newStatus(), session.csrfTokenHash(), session.activeOtpChallengeId(), session.data(), Instant.now(clock)));
    }

    public Mono<AuthFlowSession> updateActiveOtpChallenge(UpdateAuthFlowOtpCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return mutateUsable(command.authFlowSessionId(), session -> copy(session, session.status(), session.csrfTokenHash(), command.activeOtpChallengeId(), session.data(), Instant.now(clock)));
    }

    public Mono<AuthFlowSession> updateDestination(UpdateAuthFlowDestinationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return mutateUsable(command.authFlowSessionId(), session -> {
            var currentData = session.data() == null ? AuthFlowSessionData.empty() : session.data();
            var updatedData = new AuthFlowSessionData(command.contactType(), command.maskedDestination(), currentData.attributes());
            return copy(session, session.status(), session.csrfTokenHash(), session.activeOtpChallengeId(), updatedData, Instant.now(clock));
        });
    }


    public Mono<AuthFlowSession> complete(CompleteAuthFlowSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return authFlowSessionPort.complete(command.authFlowSessionId(), Instant.now(clock));
    }

    public Mono<AuthFlowSession> cancel(CancelAuthFlowSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return authFlowSessionPort.cancel(command.authFlowSessionId(), Instant.now(clock));
    }

    public Mono<Void> delete(DeleteAuthFlowSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return authFlowSessionPort.delete(command.authFlowSessionId());
    }

    private Mono<AuthFlowSession> mutateUsable(AuthFlowSessionId id, SessionMutation mutation) {
        Instant now = Instant.now(clock);
        return authFlowSessionPort.load(id)
                .flatMap(optional -> optional.map(Mono::just).orElseGet(() -> Mono.error(new IllegalStateException("Auth flow session not available"))))
                .flatMap(session -> {
                    if (session.isExpired(now) || session.isTerminal()) {
                        return Mono.error(new IllegalStateException("Auth flow session is not usable"));
                    }
                    return Mono.just(mutation.apply(session));
                })
                .flatMap(authFlowSessionPort::update);
    }

    private static AuthFlowSession copy(AuthFlowSession session, AuthFlowStatus status, String csrfTokenHash, String activeOtpChallengeId, AuthFlowSessionData data, Instant updatedAt) {
        return new AuthFlowSession(session.authFlowSessionId(), session.scenario(), status, csrfTokenHash, activeOtpChallengeId, data, session.createdAt(), updatedAt, session.expiresAt());
    }

    @FunctionalInterface
    private interface SessionMutation {
        AuthFlowSession apply(AuthFlowSession session);
    }
}
