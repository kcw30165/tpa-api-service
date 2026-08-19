package com.bct.ngtpa.apiservice.application.dto.workflow;

public record WorkflowResultPayload<T>(WorkflowNextStep nextStep, T data) {
  public WorkflowResultPayload {
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
  }
}
