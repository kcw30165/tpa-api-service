package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import java.util.Objects;

public record StartRegistrationError(WorkflowErrorKey errorKey, String target, String message) {
  /** Creates an error that carries no message of its own: only the error key and the target. */
  public static StartRegistrationError withoutMessage(WorkflowErrorKey errorKey, String target) {
    return new StartRegistrationError(errorKey, target, null);
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
