package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import reactor.core.publisher.Mono;

/** Recovers an already committed authenticated Login directly from its opaque handoff. */
@FunctionalInterface
public interface RecoverCommittedLoginByHandoffUseCase {
  Mono<RecoverCommittedLoginResult> recover(RecoverCommittedLoginByHandoffCommand command);
}
