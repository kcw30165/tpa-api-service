package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import java.util.List;

public record RegistrationPasswordValidationResult(
    boolean valid, List<WorkflowErrorDescriptor> errors) {
  public RegistrationPasswordValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    if (valid && !errors.isEmpty()) {
      throw new IllegalArgumentException("valid result must not contain errors");
    }
  }

  public static RegistrationPasswordValidationResult success() {
    return new RegistrationPasswordValidationResult(true, List.of());
  }

  public static RegistrationPasswordValidationResult failure(List<WorkflowErrorDescriptor> errors) {
    return new RegistrationPasswordValidationResult(false, errors);
  }
}
