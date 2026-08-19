package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Capability-scoped persistence used only by committed authenticated-login replay. */
public interface AuthenticatedLoginReplaySessionPort {
  Mono<Optional<ProtectedAuthenticatedSession>> loadForReplay(
      LoadAuthenticatedSessionForReplayCommand command);

  Mono<AuthenticatedSessionCsrfRotationResult> rotateCsrfHashIfCurrent(
      RotateAuthenticatedSessionCsrfHashCommand command);
}
