package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import reactor.core.publisher.Mono;

/**
 * Framework-free inbound application contract for the Username Recovery request flow.
 *
 * <p>Repair 024A canonical entry: the richer method whose raw CSRF token and origin/referer are
 * transient method parameters and never enter any command record. The single-argument
 * compatibility method fails closed with {@code SECURITY_CONTEXT_REQUIRED} because the port cannot
 * carry raw CSRF.
 */
@FunctionalInterface
public interface RequestUsernameRecoveryUseCase {

    Mono<UsernameRecoveryRequestResult> request(
            UsernameRecoveryRequestCommand command, String rawCsrfToken, String origin, String referer);

    default Mono<UsernameRecoveryRequestResult> request(UsernameRecoveryRequestCommand command) {
        return Mono.error(UsernameRecoveryRequestException.securityContextRequired());
    }
}
