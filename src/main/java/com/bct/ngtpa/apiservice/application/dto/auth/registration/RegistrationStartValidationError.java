package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import java.util.Objects;

public record RegistrationStartValidationError(
    WorkflowErrorKey errorKey, String target, String source) {
  public RegistrationStartValidationError {
    Objects.requireNonNull(errorKey, "errorKey must not be null");
    Objects.requireNonNull(target, "target must not be null");
    source = source == null || source.isBlank() ? "LOCAL" : source;
  }

  @Override
  public String toString() {
    return "RegistrationStartValidationError[errorKey="
        + errorKey.value()
        + ", target="
        + target
        + ", source="
        + source
        + "]";
  }
}
