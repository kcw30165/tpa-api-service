package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;

public record ResolvedWorkflowErrorDefinition(
    String code,
    List<String> targets,
    WorkflowErrorType type,
    WorkflowErrorSeverity severity,
    String source,
    WorkflowNextStep nextStep) {
  public ResolvedWorkflowErrorDefinition {
    targets = targets == null ? List.of() : List.copyOf(targets);
  }
}
