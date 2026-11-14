package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;

public record CompleteForgotPasswordResult(
    CompleteForgotPasswordStatus status,
    WorkflowNextStep nextStep,
    boolean clearAuthFlowCookie,
    boolean clearXsrfCookie,
    List<WorkflowErrorDescriptor> errors,
    ForgotPasswordCompletePublicationReceipt publicationReceipt) {
  public CompleteForgotPasswordResult(
      CompleteForgotPasswordStatus status,
      WorkflowNextStep nextStep,
      boolean clearAuthFlowCookie,
      boolean clearXsrfCookie,
      List<WorkflowErrorDescriptor> errors) {
    this(status, nextStep, clearAuthFlowCookie, clearXsrfCookie, errors, null);
  }

  public CompleteForgotPasswordResult {
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public CompleteForgotPasswordResult withPublicationReceipt(
      ForgotPasswordCompletePublicationReceipt receipt) {
    return new CompleteForgotPasswordResult(
        status,
        nextStep,
        clearAuthFlowCookie,
        clearXsrfCookie,
        errors,
        receipt);
  }
}
