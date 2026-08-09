package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import reactor.core.publisher.Mono;

/**
 * Framework-free inbound application contract for the Username Recovery request flow.
 */
@FunctionalInterface
public interface RequestUsernameRecoveryUseCase {

    Mono<UsernameRecoveryRequestResult> request(UsernameRecoveryRequestCommand command);
}
