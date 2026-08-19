package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

/**
 * Feature payload/result record for Username Recovery endpoints: the approved {@code nextStep} plus
 * a nullable {@code data} projection.
 *
 * <p>Follows the established Login/Registration/ForgotPassword {@code
 * XxxWorkflowResultResponse<T>(nextStep, data)} convention. A null {@code nextStep} defaults to
 * {@link WorkflowNextStep#NONE}. Task 022 mappers always pass an explicit next step; Task 023 owns
 * HTTP status, messages, errors, and response assembly around this record.
 */
public record UsernameRecoveryWorkflowResultResponse<T>(WorkflowNextStep nextStep, T data) {

  public UsernameRecoveryWorkflowResultResponse {
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
  }
}
