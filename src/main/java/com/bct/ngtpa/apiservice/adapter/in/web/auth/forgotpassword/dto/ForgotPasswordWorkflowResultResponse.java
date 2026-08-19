package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record ForgotPasswordWorkflowResultResponse<T>(WorkflowNextStep nextStep, T data) {
  public ForgotPasswordWorkflowResultResponse {
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
  }
}
