package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

/** Input to {@link GetAccountsUseCase}: the session whose accounts should be listed. */
public record GetAccountsCommand(AuthenticatedSessionId sessionId) {
  public GetAccountsCommand {
    Objects.requireNonNull(sessionId);
  }
}
