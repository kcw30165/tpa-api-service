package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AuthenticatedLogoutUseCase {
  Mono<AuthenticatedLogoutResult> logout(AuthenticatedLogoutCommand command);
}
