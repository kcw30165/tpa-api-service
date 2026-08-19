package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import reactor.core.publisher.Mono;

/** Outbound account authorization capability implemented by the shared APIM adapter layer. */
@FunctionalInterface
public interface LoginAccountAuthorizationPort {
  Mono<LoginAccountAuthorizationResult> getAccounts(LoginAccountAuthorizationCommand command);
}
