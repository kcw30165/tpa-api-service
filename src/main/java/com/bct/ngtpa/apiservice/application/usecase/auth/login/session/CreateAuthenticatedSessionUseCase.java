package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CreateAuthenticatedSessionUseCase {
  Mono<AuthenticatedSessionLifecycleResult> create(CreateAuthenticatedSessionCommand command);
}
