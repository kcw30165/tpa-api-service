package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class EstablishAuthenticatedLoginSessionService implements EstablishAuthenticatedLoginSessionUseCase {
    private final CreateAuthenticatedSessionUseCase create;
    private final DeleteAuthFlowSessionPort cleanup;
    private final RevokeAuthenticatedSessionUseCase revoke;

    public EstablishAuthenticatedLoginSessionService(CreateAuthenticatedSessionUseCase create,
            DeleteAuthFlowSessionPort cleanup, RevokeAuthenticatedSessionUseCase revoke) {
        this.create = Objects.requireNonNull(create);
        this.cleanup = Objects.requireNonNull(cleanup);
        this.revoke = Objects.requireNonNull(revoke);
    }

    public Mono<EstablishAuthenticatedLoginSessionResult> establish(EstablishAuthenticatedLoginSessionCommand command) {
        var createCommand = new CreateAuthenticatedSessionCommand(command.actorContext(),
                LoginAuthenticatedSessionAccountMapper.map(command.accounts()), command.csrfTokenHash(),
                command.correlationId());
        return create.create(createCommand).flatMap(created -> {
            if (created.status() != AuthenticatedSessionLifecycleStatus.ACTIVE || created.session() == null)
                return Mono.just(EstablishAuthenticatedLoginSessionResult
                        .failed(EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
            var id = created.session().authenticatedSessionId();
            return cleanup.delete(command.authFlowSessionId())
                    .thenReturn(EstablishAuthenticatedLoginSessionResult.established(id))
                    .onErrorResume(error -> revoke
                            .revoke(new RevokeActiveAuthenticatedSessionCommand(id, "PRE_AUTH_CLEANUP_FAILED",
                                    command.correlationId()))
                            .onErrorResume(ignored -> Mono.just(
                                    AuthenticatedSessionLifecycleResult.of(AuthenticatedSessionLifecycleStatus.FAILED)))
                            .thenReturn(EstablishAuthenticatedLoginSessionResult
                                    .failed(EstablishAuthenticatedLoginSessionStatus.CLEANUP_FAILED)));
        }).onErrorReturn(EstablishAuthenticatedLoginSessionResult
                .failed(EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
    }
}
