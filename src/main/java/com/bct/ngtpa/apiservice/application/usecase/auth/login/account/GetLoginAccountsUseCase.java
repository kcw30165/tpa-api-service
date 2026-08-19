package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Builds the exact TRPGetAccountList request solely from protected pre-auth values. */
public final class GetLoginAccountsUseCase {
  private static final String SEARCH_KEY = "cust-id";
  private final LoginAccountAuthorizationPort port;

  public GetLoginAccountsUseCase(LoginAccountAuthorizationPort port) {
    this.port = Objects.requireNonNull(port);
  }

  public Mono<LoginAccountAuthorizationResult> getAccounts(GetLoginAccountsCommand command) {
    Objects.requireNonNull(command);
    return port.getAccounts(
        new LoginAccountAuthorizationCommand(
            command.normalizedUsername(),
            SEARCH_KEY,
            command.normalizedUsername(),
            command.correlationId(),
            command.actorUserRole()));
  }
}
