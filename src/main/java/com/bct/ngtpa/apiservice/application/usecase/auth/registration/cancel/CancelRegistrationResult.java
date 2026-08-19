package com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import java.util.List;
import java.util.Objects;

public record CancelRegistrationResult(
    CancelRegistrationResultStatus status,
    boolean clearAuthFlowCookie,
    boolean clearXsrfCookie,
    List<WorkflowErrorDescriptor> errors) {

  public CancelRegistrationResult {
    Objects.requireNonNull(status, "status must not be null");
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public boolean successfulCleanup() {
    return switch (status) {
      case CANCELLED, SESSION_NOT_FOUND, ALREADY_CANCELLED, ALREADY_TERMINAL -> true;
      default -> false;
    };
  }
}
