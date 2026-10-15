package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

/** Input to {@link GetAccountUseCase}: the session performing the lookup and its target. */
public record GetAccountCommand(AuthenticatedSessionId sessionId, String targetAccountRef) {
  public GetAccountCommand {
    Objects.requireNonNull(sessionId);
    Objects.requireNonNull(targetAccountRef);
  }
}
