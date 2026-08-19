package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import reactor.core.publisher.Mono;

/**
 * Framework-free inbound application contract for the Username Recovery cancel flow.
 *
 * <p>Repair 024A canonical entry: the richer method whose raw CSRF token and origin/referer are
 * transient method parameters and never enter any command record. The single-argument compatibility
 * method fails closed with {@code SECURITY_CONTEXT_REQUIRED} because the port cannot carry raw
 * CSRF.
 */
@FunctionalInterface
public interface CancelUsernameRecoveryUseCase {

  Mono<UsernameRecoveryCancelResult> cancel(
      UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer);

  default Mono<UsernameRecoveryCancelResult> cancel(UsernameRecoveryCancelCommand command) {
    return Mono.error(UsernameRecoveryCancelException.securityContextRequired());
  }
}
