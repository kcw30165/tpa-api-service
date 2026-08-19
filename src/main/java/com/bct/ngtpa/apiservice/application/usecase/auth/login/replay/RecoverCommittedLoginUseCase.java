package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface RecoverCommittedLoginUseCase {
  Mono<RecoverCommittedLoginResult> recover(RecoverCommittedLoginCommand command);
}
