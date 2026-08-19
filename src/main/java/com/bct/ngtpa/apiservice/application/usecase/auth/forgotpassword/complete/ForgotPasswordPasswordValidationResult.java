package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import java.util.List;

public record ForgotPasswordPasswordValidationResult(
    boolean valid, List<WorkflowErrorDescriptor> errors) {
  public ForgotPasswordPasswordValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    if (valid && !errors.isEmpty()) {
      throw new IllegalArgumentException("valid result must not contain errors");
    }
  }

  public static ForgotPasswordPasswordValidationResult success() {
    return new ForgotPasswordPasswordValidationResult(true, List.of());
  }

  public static ForgotPasswordPasswordValidationResult failure(
      List<WorkflowErrorDescriptor> errors) {
    return new ForgotPasswordPasswordValidationResult(false, errors);
  }
}
