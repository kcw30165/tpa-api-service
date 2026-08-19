package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

/** Shared public workflow action. Deliberately contains only the stable code. */
public record WorkflowActionResponse(WorkflowActionCode code) {
  public WorkflowActionResponse {
    Objects.requireNonNull(code, "code must not be null");
  }
}
