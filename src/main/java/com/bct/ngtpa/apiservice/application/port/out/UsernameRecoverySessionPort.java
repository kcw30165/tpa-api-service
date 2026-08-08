package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import reactor.core.publisher.Mono;

/** Outbound port for protected Username Recovery session persistence. */
public interface UsernameRecoverySessionPort {

    Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession session);

    Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId sessionId);

    Mono<UsernameRecoverySessionMutationResult> update(
            UsernameRecoverySessionMutationCommand command);

    Mono<UsernameRecoverySessionDeleteResult> delete(UsernameRecoverySessionId sessionId);
}
