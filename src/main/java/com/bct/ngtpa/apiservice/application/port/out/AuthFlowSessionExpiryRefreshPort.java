package com.bct.ngtpa.apiservice.application.port.out;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import reactor.core.publisher.Mono;
public interface AuthFlowSessionExpiryRefreshPort {
    Mono<AuthFlowSession> refresh(AuthFlowSessionId sessionId, Instant interactionAt);
}
