package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ValidateAuthenticatedSessionUseCase {
  Mono<AuthenticatedSession> validate(ValidateAuthenticatedSessionCommand command);
}
