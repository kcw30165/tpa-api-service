package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.InitializeLegacyAuthFlowSessionRevisionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.LegacyAuthFlowSessionRevisionInitializationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionDeleteCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionDeleteResult;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface AuthFlowSessionPort {
    Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command);

    default Mono<AuthFlowSessionSnapshot> createRevisioned(AuthFlowSessionCreateCommand command) {
        return Mono.error(new UnsupportedOperationException("Revision-aware create is not implemented"));
    }

    Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId);

    default Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId authFlowSessionId) {
        return Mono.error(new UnsupportedOperationException("Snapshot load is not implemented"));
    }

    default Mono<LegacyAuthFlowSessionRevisionInitializationResult> initializeLegacyRevision(
            InitializeLegacyAuthFlowSessionRevisionCommand command) {
        return Mono.error(new UnsupportedOperationException("Legacy revision initialization is not implemented"));
    }

    default Mono<AuthFlowSessionMutationResult> updateRevisioned(
            RevisionedAuthFlowSessionMutationCommand command) {
        return Mono.error(new UnsupportedOperationException("Revision-aware update is not implemented"));
    }

    default Mono<AuthFlowSessionMutationResult> touchRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
        return Mono.error(new UnsupportedOperationException("Revision-aware touch is not implemented"));
    }

    default Mono<AuthFlowSessionMutationResult> completeRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
        return Mono.error(new UnsupportedOperationException("Revision-aware complete is not implemented"));
    }

    default Mono<AuthFlowSessionMutationResult> cancelRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
        return Mono.error(new UnsupportedOperationException("Revision-aware cancel is not implemented"));
    }

    default Mono<AuthFlowSessionDeleteResult> deleteRevisioned(RevisionedAuthFlowSessionDeleteCommand command) {
        return Mono.error(new UnsupportedOperationException("Revision-aware delete is not implemented"));
    }

    Mono<AuthFlowSession> update(AuthFlowSession session);

    Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now);

    Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now);

    Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now);

    Mono<Void> delete(AuthFlowSessionId authFlowSessionId);
}
