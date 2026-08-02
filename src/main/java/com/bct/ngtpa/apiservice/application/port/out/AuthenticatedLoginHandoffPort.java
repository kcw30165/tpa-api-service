package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import reactor.core.publisher.Mono;

public interface AuthenticatedLoginHandoffPort {
    Mono<AuthenticatedLoginHandoffCreateResult> createIfAbsent(CreateAuthenticatedLoginHandoffCommand command);

    Mono<AuthenticatedLoginHandoff> loadByHandoff(AuthenticatedLoginHandoffReference reference);

    Mono<AuthenticatedLoginHandoff> recordAuthenticatedSessionCommitted(
            RecordAuthenticatedSessionCommittedCommand command);

    Mono<AuthenticatedLoginHandoff> recordCleanupProof(RecordAuthFlowCleanupProofCommand command);
}
