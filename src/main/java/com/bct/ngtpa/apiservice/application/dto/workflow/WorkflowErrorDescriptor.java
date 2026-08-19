package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.List;
import java.util.Objects;

public record WorkflowErrorDescriptor(
    WorkflowErrorType type,
    String code,
    String message,
    List<String> targets,
    WorkflowErrorSeverity severity,
    String source) {
  public WorkflowErrorDescriptor {
    type = type == null ? WorkflowErrorType.FORM : type;
    Objects.requireNonNull(code, "code must not be null");
    if (code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    targets = targets == null ? List.of() : List.copyOf(targets);
    severity = severity == null ? WorkflowErrorSeverity.ERROR : severity;
    source = source == null || source.isBlank() ? "SERVER" : source;
  }
}
