package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record LoginWorkflowResultResponse<T>(WorkflowNextStep nextStep, T data) {
  public LoginWorkflowResultResponse {
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
  }
}
