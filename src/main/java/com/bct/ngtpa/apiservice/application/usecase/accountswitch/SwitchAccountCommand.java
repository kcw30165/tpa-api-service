package com.bct.ngtpa.apiservice.application.usecase.accountswitch;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

/** Input to {@link SwitchAccountUseCase}: the session performing the switch and its target. */
public record SwitchAccountCommand(AuthenticatedSessionId sessionId, String targetAccountRef) {
  public SwitchAccountCommand {
    Objects.requireNonNull(sessionId);
    Objects.requireNonNull(targetAccountRef);
  }
}
