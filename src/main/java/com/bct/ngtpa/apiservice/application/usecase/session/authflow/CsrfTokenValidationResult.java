package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import java.util.Objects;

public record CsrfTokenValidationResult(CsrfTokenValidationStatus status) {
  public CsrfTokenValidationResult {
    Objects.requireNonNull(status, "status must not be null");
  }

  public boolean valid() {
    return status == CsrfTokenValidationStatus.VALID;
  }
}
