package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.UsernameRecoveryErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

/**
 * Typed Cancel response policy: maps every Cancel result outcome and every
 * Cancel exception reason into a completed {@code MutationResponse} plus an
 * optional typed HTTP classification override, reusing the Task 022 Cancel
 * mapper projection as the only success data source.
 *
 * <p>Cookie clearing is a Task 024 transport concern and is never modeled
 * here. This class never chooses an HTTP status, never reads headers/cookies,
 * and never catches arbitrary exceptions.
 */
public final class UsernameRecoveryCancelResponsePolicy {

    public UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> toPolicy(
            UsernameRecoveryCancelResult result, UsernameRecoveryCancelWebMapper projectionMapper) {
        Objects.requireNonNull(result, "username recovery cancel result required");
        Objects.requireNonNull(projectionMapper, "username recovery cancel projection mapper required");
        return switch (result.outcome()) {
            case SUCCESS -> UsernameRecoveryWorkflowMapping.policy(
                    MutationResponse.success(ApiStatus.SUCCESS, projectionMapper.toResult(result), List.of()));
            case SYSTEM_FAILED -> systemFailure(WorkflowNextStep.RETURN_TO_LOGIN);
        };
    }

    public UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> toPolicy(
            UsernameRecoveryCancelException exception) {
        Objects.requireNonNull(exception, "username recovery cancel exception required");
        return switch (exception.reason()) {
            case VALIDATION_FAILED -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            ErrorCodes.REQUEST_VALIDATION_FAILED,
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    null);
            case SECURITY_CONTEXT_REQUIRED -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.authenticationRequiredError()),
                    null);
            case SECURITY_INVALID -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            UsernameRecoveryErrorKey.CSRF_INVALID.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_SERVER)),
                    HttpStatusClassificationOverride.FORBIDDEN);
            case IDEMPOTENCY_IN_PROGRESS -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.businessError(
                            SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case IDEMPOTENCY_KEY_REUSED -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                            List.of(UsernameRecoveryWorkflowMapping.TARGET_IDEMPOTENCY_KEY),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case UNKNOWN_OUTCOME_EXISTING -> systemFailure(WorkflowNextStep.RETURN_TO_LOGIN);
            case REVISION_CONFLICT -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.businessError(
                            SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case SYSTEM_FAILURE -> systemFailure(WorkflowNextStep.RETURN_TO_LOGIN);
        };
    }

    private UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> systemFailure(
            WorkflowNextStep nextStep) {
        return failure(
                ApiStatus.SYSTEM_ERROR,
                nextStep,
                List.of(UsernameRecoveryWorkflowMapping.systemError(
                        SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                        UsernameRecoveryWorkflowMapping.SOURCE_SERVER)),
                null);
    }

    private UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> failure(
            ApiStatus status, WorkflowNextStep nextStep, List<ApiError> errors,
            HttpStatusClassificationOverride override) {
        return UsernameRecoveryWorkflowMapping.policy(
                UsernameRecoveryWorkflowMapping.failureResponse(status, nextStep, errors), override);
    }
}