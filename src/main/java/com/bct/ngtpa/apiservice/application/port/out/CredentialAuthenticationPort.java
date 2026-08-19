package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import reactor.core.publisher.Mono;

/** Outbound boundary for credential authority authentication. */
@FunctionalInterface
public interface CredentialAuthenticationPort {
  Mono<CredentialAuthenticationResult> authenticate(CredentialAuthenticationCommand command);
}
