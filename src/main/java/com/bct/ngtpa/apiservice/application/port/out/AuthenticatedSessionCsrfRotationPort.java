package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.RotateAuthenticatedSessionCsrfHashCommand;
import reactor.core.publisher.Mono;

/** Atomic capability for replacing the CSRF hash of an authenticated session. */
public interface AuthenticatedSessionCsrfRotationPort {
  Mono<AuthenticatedSessionCsrfRotationResult> rotateCsrfHashIfCurrent(
      RotateAuthenticatedSessionCsrfHashCommand command);
}
