package com.bct.ngtpa.apiservice.application.port.out;

import java.util.Objects;

public record AuthFlowSessionPersistenceCompatibilityResult(
    AuthFlowSessionPersistenceCompatibilityStatus status,
    int expectedVersion,
    Integer actualVersion) {
  public AuthFlowSessionPersistenceCompatibilityResult {
    Objects.requireNonNull(status, "status must not be null");
  }

  public boolean compatible() {
    return status == AuthFlowSessionPersistenceCompatibilityStatus.COMPATIBLE
        || status == AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZED;
  }
}
