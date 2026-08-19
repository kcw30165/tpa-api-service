package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.RevokeActiveAuthenticatedSessionCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface RevokeAuthenticatedSessionUseCase {
  Mono<AuthenticatedSessionLifecycleResult> revoke(RevokeActiveAuthenticatedSessionCommand command);
}
