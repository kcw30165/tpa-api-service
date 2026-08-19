package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.Objects;

/**
 * Stateless web mapper for Username Recovery {@code /request} result projections.
 *
 * <p>Only {@code REQUEST_ACCEPTED} carries an approved success projection. The accepted-result
 * invariant restricts {@code workflowStatus} to {@code PARTIALLY_REQUESTED} or {@code COMPLETED},
 * so the switch below maps {@code COMPLETED -> RETURN_TO_LOGIN} and everything else (i.e. {@code
 * PARTIALLY_REQUESTED}) to {@code SELECT_ACCOUNT}. The masked destination is copied verbatim and
 * never remasked; no raw destination or username is ever accepted.
 *
 * <p>Non-accepted outcomes carry no success data: they project null data with nextStep {@code
 * NONE}. Task 023 owns the response policies (HTTP status, errors, nextStep) for those outcomes.
 * This mapper never chooses HTTP status, never builds {@code MutationResponse}, never reads
 * headers/cookies, and never catches application exceptions.
 */
public final class UsernameRecoveryRequestWebMapper {

  public UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse> toResult(
      UsernameRecoveryRequestResult result) {
    Objects.requireNonNull(result, "username recovery request result required");
    if (result.outcome() != UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED) {
      return new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.NONE, null);
    }
    WorkflowNextStep nextStep =
        switch (result.workflowStatus()) {
          case COMPLETED -> WorkflowNextStep.RETURN_TO_LOGIN;
          default -> WorkflowNextStep.SELECT_ACCOUNT;
        };
    return new UsernameRecoveryWorkflowResultResponse<>(
        nextStep,
        new UsernameRecoveryRequestDataResponse(
            result.workflowStatus().name(),
            result.schemeRef().value(),
            result.maskedDestination(),
            result.destinationType().name(),
            true));
  }
}
