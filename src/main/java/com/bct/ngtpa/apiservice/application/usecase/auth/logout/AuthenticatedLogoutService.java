package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.RevokeActiveAuthenticatedSessionCommand;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class AuthenticatedLogoutService implements AuthenticatedLogoutUseCase {
    static final String USER_LOGOUT_REASON = "USER_LOGOUT";

    private final RevokeAuthenticatedSessionUseCase revoke;

    public AuthenticatedLogoutService(RevokeAuthenticatedSessionUseCase revoke) {
        this.revoke = Objects.requireNonNull(revoke, "revoke must not be null");
    }

    @Override
    public Mono<AuthenticatedLogoutResult> logout(AuthenticatedLogoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var revokeCommand = new RevokeActiveAuthenticatedSessionCommand(
                command.authenticatedSessionId(),
                USER_LOGOUT_REASON,
                command.correlationId());
        return revoke.revoke(revokeCommand)
                .map(result -> map(result.status()))
                .onErrorReturn(AuthenticatedLogoutResult.failed());
    }

    private AuthenticatedLogoutResult map(AuthenticatedSessionLifecycleStatus status) {
        if (status == AuthenticatedSessionLifecycleStatus.REVOKED) {
            return AuthenticatedLogoutResult.loggedOut();
        }
        if (status == AuthenticatedSessionLifecycleStatus.NOT_FOUND
                || status == AuthenticatedSessionLifecycleStatus.EXPIRED) {
            return AuthenticatedLogoutResult.alreadyLoggedOut();
        }
        return AuthenticatedLogoutResult.failed();
    }
}
