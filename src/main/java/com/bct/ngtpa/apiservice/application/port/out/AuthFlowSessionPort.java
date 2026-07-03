package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface AuthFlowSessionPort {
    Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command);
    Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId);
    Mono<AuthFlowSession> update(AuthFlowSession session);
    Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now);
    Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now);
    Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now);
    Mono<Void> delete(AuthFlowSessionId authFlowSessionId);
}
