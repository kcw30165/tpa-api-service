package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import java.util.Objects;

public record StartRegistrationError(WorkflowErrorKey errorKey, String target, String message) {
  public StartRegistrationError(WorkflowErrorKey errorKey, String target) {
    this(errorKey, target, null);
  }

  public StartRegistrationError {
    Objects.requireNonNull(errorKey, "errorKey must not be null");
    target = target == null || target.isBlank() ? "PAGE" : target;
    message = message == null || message.isBlank() ? null : message;
  }

  @Override
  public String toString() {
    return "StartRegistrationError[errorKey="
        + errorKey.value()
        + ", target="
        + target
        + ", message="
        + (message == null ? "<none>" : "<localized>")
        + "]";
  }
}
