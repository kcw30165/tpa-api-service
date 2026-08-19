package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.Objects;

/**
 * Stateless web mapper for Username Recovery {@code /cancel} result projections.
 *
 * <p>A successful cancellation projects {@code RETURN_TO_LOGIN} with null data (no empty object).
 * {@code SYSTEM_FAILED} projects null data with nextStep {@code NONE}; Task 023 owns the
 * system-error response policy. Cookie clearing is a Task 024 transport concern and is not modeled
 * here.
 */
public final class UsernameRecoveryCancelWebMapper {

  public UsernameRecoveryWorkflowResultResponse<Void> toResult(
      UsernameRecoveryCancelResult result) {
    Objects.requireNonNull(result, "username recovery cancel result required");
    if (result.outcome() == UsernameRecoveryCancelResult.Outcome.SUCCESS) {
      return new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.RETURN_TO_LOGIN, null);
    }
    return new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.NONE, null);
  }
}
