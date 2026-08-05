package com.bct.ngtpa.apiservice.adapter.out.session;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCancellationReason;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * AuthFlowSession specialization of the generic cancellation capability.
 * HTTP cookie clearing remains an inbound web responsibility.
 */
public class AuthFlowSessionCancellationAdapter implements AuthFlowSessionCancellationPort {

    private final AuthFlowSessionPort authFlowSessionPort;

    public AuthFlowSessionCancellationAdapter(AuthFlowSessionPort authFlowSessionPort) {
        this.authFlowSessionPort = Objects.requireNonNull(
                authFlowSessionPort,
                "authFlowSessionPort must not be null");
    }

    @Override
    public Mono<SessionCancellationResult> cancel(
            AuthFlowSessionId sessionId,
            AuthFlowSessionCancellationReason reason,
            Instant cancelledAt) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");

        return authFlowSessionPort.load(sessionId)
                .flatMap(optional -> optional
                        .map(session -> {
                            if (session.status() == AuthFlowStatus.CANCELLED) {
                                return Mono.just(result(SessionCancellationStatus.ALREADY_CANCELLED));
                            }
                            if (session.isTerminal()) {
                                return Mono.just(result(SessionCancellationStatus.ALREADY_TERMINAL));
                            }
                            return new com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter(authFlowSessionPort)
                                    .cancel(sessionId, cancelledAt)
                                    .map(ignored -> result(SessionCancellationStatus.CANCELLED));
                        })
                        .orElseGet(() -> Mono.just(result(SessionCancellationStatus.NOT_FOUND))));
    }

    private static SessionCancellationResult result(SessionCancellationStatus status) {
        return new SessionCancellationResult(status);
    }
}
