package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import reactor.core.publisher.Mono;

/**
 * Framework-free inbound application contract for the Username Recovery cancel flow.
 */
@FunctionalInterface
public interface CancelUsernameRecoveryUseCase {

    Mono<UsernameRecoveryCancelResult> cancel(UsernameRecoveryCancelCommand command);
}
