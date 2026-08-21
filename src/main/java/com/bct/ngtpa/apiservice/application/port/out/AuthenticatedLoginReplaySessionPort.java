package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Capability-scoped persistence used only by committed authenticated-login replay. */
public interface AuthenticatedLoginReplaySessionPort extends AuthenticatedSessionCsrfRotationPort {
  Mono<Optional<ProtectedAuthenticatedSession>> loadForReplay(
      LoadAuthenticatedSessionForReplayCommand command);
}
