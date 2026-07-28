package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class EstablishAuthenticatedLoginSessionService
        implements EstablishAuthenticatedLoginSessionUseCase {
    private final CreateAuthenticatedSessionUseCase create;
    private final DeleteAuthFlowSessionPort cleanup;
    private final RevokeAuthenticatedSessionUseCase revoke;
    private final CsrfTokenGenerator csrfTokenGenerator;
    private final CsrfTokenHasher csrfTokenHasher;

    public EstablishAuthenticatedLoginSessionService(
            CreateAuthenticatedSessionUseCase create,
            DeleteAuthFlowSessionPort cleanup,
            RevokeAuthenticatedSessionUseCase revoke,
            CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher) {
        this.create = Objects.requireNonNull(create);
        this.cleanup = Objects.requireNonNull(cleanup);
        this.revoke = Objects.requireNonNull(revoke);
        this.csrfTokenGenerator = Objects.requireNonNull(csrfTokenGenerator);
        this.csrfTokenHasher = Objects.requireNonNull(csrfTokenHasher);
    }

    @Override
    public Mono<EstablishAuthenticatedLoginSessionResult> establish(
            EstablishAuthenticatedLoginSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return Mono.defer(() -> {
            String rawAuthenticatedCsrfToken = csrfTokenGenerator.generate();
            if (rawAuthenticatedCsrfToken == null || rawAuthenticatedCsrfToken.isBlank()) {
                return Mono.just(EstablishAuthenticatedLoginSessionResult.failed(
                        EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
            }
            String authenticatedCsrfHash = csrfTokenHasher.hash(rawAuthenticatedCsrfToken);
            var createCommand = new CreateAuthenticatedSessionCommand(
                    command.actorContext(),
                    LoginAuthenticatedSessionAccountMapper.map(command.accounts()),
                    authenticatedCsrfHash,
                    command.correlationId());
            return create.create(createCommand)
                    .flatMap(created -> {
                        if (created.status() != AuthenticatedSessionLifecycleStatus.ACTIVE
                                || created.session() == null) {
                            return Mono.just(EstablishAuthenticatedLoginSessionResult.failed(
                                    EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
                        }
                        var id = created.session().authenticatedSessionId();
                        return cleanup.delete(command.authFlowSessionId())
                                .thenReturn(EstablishAuthenticatedLoginSessionResult.established(
                                        id, rawAuthenticatedCsrfToken))
                                .onErrorResume(error -> revoke.revoke(
                                                new RevokeActiveAuthenticatedSessionCommand(
                                                        id,
                                                        "PRE_AUTH_CLEANUP_FAILED",
                                                        command.correlationId()))
                                        .onErrorResume(ignored -> Mono.just(
                                                AuthenticatedSessionLifecycleResult.of(
                                                        AuthenticatedSessionLifecycleStatus.FAILED)))
                                        .thenReturn(EstablishAuthenticatedLoginSessionResult.failed(
                                                EstablishAuthenticatedLoginSessionStatus.CLEANUP_FAILED)));
                    });
        }).onErrorReturn(EstablishAuthenticatedLoginSessionResult.failed(
                EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
    }
}
