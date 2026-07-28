package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import reactor.core.publisher.Mono;

/** Narrow lifecycle capability for the normal ACTIVE to LOGGED_OUT transition. */
@FunctionalInterface
public interface LogoutAuthenticatedSessionUseCase {
    Mono<AuthenticatedSessionLifecycleResult> logout(
            LogoutActiveAuthenticatedSessionCommand command);
}
