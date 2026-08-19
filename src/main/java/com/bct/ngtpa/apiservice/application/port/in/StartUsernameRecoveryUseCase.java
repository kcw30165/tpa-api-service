package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import reactor.core.publisher.Mono;

/** Framework-free inbound application contract for the Username Recovery start flow. */
@FunctionalInterface
public interface StartUsernameRecoveryUseCase {

  Mono<UsernameRecoveryStartResult> start(UsernameRecoveryStartCommand command);
}
