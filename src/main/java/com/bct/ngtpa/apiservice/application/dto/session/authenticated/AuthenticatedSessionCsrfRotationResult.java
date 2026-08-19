package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.util.Objects;

public record AuthenticatedSessionCsrfRotationResult(
    AuthenticatedSessionCsrfRotationStatus status) {
  public AuthenticatedSessionCsrfRotationResult {
    Objects.requireNonNull(status);
  }
}
