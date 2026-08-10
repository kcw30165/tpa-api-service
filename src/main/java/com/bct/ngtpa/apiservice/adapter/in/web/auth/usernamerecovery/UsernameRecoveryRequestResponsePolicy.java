package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.UsernameRecoveryErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

/**
 * Typed Request response policy: maps every Request result outcome (including
 * the PARTIALLY_REQUESTED/COMPLETED accepted branches) and every Request
 * exception reason into a completed {@code MutationResponse} plus an optional
 * typed HTTP classification override, reusing the Task 022 Request mapper
 * projection as the only success data source.
 *
 * <p>The stable unknown-outcome policy (WAIT + sanitized system code) stays
 * internally distinct from ordinary system failure (SELECT_ACCOUNT) and never
 * instructs a blind resend. This class never chooses an HTTP status, never
 * reads headers/cookies, and never catches arbitrary exceptions.
 */
public final class UsernameRecoveryRequestResponsePolicy {

    public UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>> toPolicy(
            UsernameRecoveryRequestResult result, UsernameRecoveryRequestWebMapper projectionMapper) {
        Objects.requireNonNull(result, "username recovery request result required");
        Objects.requireNonNull(projectionMapper, "username recovery request projection mapper required");
        return switch (result.outcome()) {
            case REQUEST_ACCEPTED -> accepted(result, projectionMapper);
            case ALREADY_REQUESTED -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.SELECT_ACCOUNT,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            UsernameRecoveryErrorKey.REQUEST_ALREADY_REQUESTED.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case WORKFLOW_COMPLETED -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.RETURN_TO_LOGIN,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            UsernameRecoveryErrorKey.WORKFLOW_COMPLETED.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case UNREGISTERED_CANDIDATE -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.SELECT_ACCOUNT,
                    List.of(UsernameRecoveryWorkflowMapping.businessError(
                            UsernameRecoveryErrorKey.REGISTRATION_REQUIRED.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    null);
            case INVALID_SCHEME_REF -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.SELECT_ACCOUNT,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            UsernameRecoveryErrorKey.SCHEME_REF_INVALID.value(),
                            List.of(UsernameRecoveryWorkflowMapping.TARGET_SCHEME_REF),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    null);
            case SYSTEM_FAILED -> systemFailure(WorkflowNextStep.SELECT_ACCOUNT);
            case UNKNOWN_OUTCOME -> systemFailure(WorkflowNextStep.WAIT);
        };
    }

    public UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>> toPolicy(
            UsernameRecoveryRequestException exception) {
        Objects.requireNonNull(exception, "username recovery request exception required");
        return switch (exception.reason()) {
            case VALIDATION_FAILED -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.NONE,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            ErrorCodes.REQUEST_VALIDATION_FAILED,
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    null);
            case SECURITY_CONTEXT_REQUIRED -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.RESTART_FLOW,
                    List.of(UsernameRecoveryWorkflowMapping.authenticationRequiredError()),
                    null);
            case IDEMPOTENCY_IN_PROGRESS -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.WAIT,
                    List.of(UsernameRecoveryWorkflowMapping.businessError(
                            SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case IDEMPOTENCY_KEY_REUSED -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.NONE,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                            List.of(UsernameRecoveryWorkflowMapping.TARGET_IDEMPOTENCY_KEY),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case UNKNOWN_OUTCOME_EXISTING -> systemFailure(WorkflowNextStep.WAIT);
            case SESSION_ABSENT -> sessionFailure();
            case SESSION_EXPIRED -> sessionFailure();
            case SECURITY_INVALID -> failure(
                    ApiStatus.VALIDATION_FAILED,
                    WorkflowNextStep.NONE,
                    List.of(UsernameRecoveryWorkflowMapping.formError(
                            UsernameRecoveryErrorKey.CSRF_INVALID.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_SERVER)),
                    HttpStatusClassificationOverride.FORBIDDEN);
            case CANDIDATE_NOT_AVAILABLE -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.SELECT_ACCOUNT,
                    List.of(UsernameRecoveryWorkflowMapping.businessError(
                            UsernameRecoveryErrorKey.REGISTRATION_REQUIRED.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    null);
            case REVISION_CONFLICT -> failure(
                    ApiStatus.BUSINESS_REJECTED,
                    WorkflowNextStep.NONE,
                    List.of(UsernameRecoveryWorkflowMapping.businessError(
                            SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                            List.of(),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
                    HttpStatusClassificationOverride.CONFLICT);
            case SYSTEM_FAILURE -> systemFailure(WorkflowNextStep.SELECT_ACCOUNT);
        };
    }

    private UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>> accepted(
            UsernameRecoveryRequestResult result, UsernameRecoveryRequestWebMapper projectionMapper) {
        var projection = projectionMapper.toResult(result);
        boolean completed = result.workflowStatus() == UsernameRecoveryWorkflowStatus.COMPLETED;
        List<ApiMessage> messages = completed
                ? List.of(
                        UsernameRecoveryWorkflowMapping.infoMessage(
                                UsernameRecoveryWorkflowMapping.MESSAGE_REQUEST_ACCEPTED),
                        UsernameRecoveryWorkflowMapping.infoMessage(
                                UsernameRecoveryWorkflowMapping.MESSAGE_RETURN_TO_LOGIN))
                : List.of(UsernameRecoveryWorkflowMapping.infoMessage(
                        UsernameRecoveryWorkflowMapping.MESSAGE_REQUEST_ACCEPTED));
        return UsernameRecoveryWorkflowMapping.policy(
                MutationResponse.success(ApiStatus.SUCCESS, projection, messages));
    }

    private UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>> sessionFailure() {
        return failure(
                ApiStatus.VALIDATION_FAILED,
                WorkflowNextStep.RESTART_FLOW,
                List.of(UsernameRecoveryWorkflowMapping.formError(
                        UsernameRecoveryErrorKey.AUTH_FLOW_INVALID.value(),
                        List.of(),
                        UsernameRecoveryWorkflowMapping.SOURCE_SERVER)),
                null);
    }

    private UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>> systemFailure(
            WorkflowNextStep nextStep) {
        return failure(
                ApiStatus.SYSTEM_ERROR,
                nextStep,
                List.of(UsernameRecoveryWorkflowMapping.systemError(
                        SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                        UsernameRecoveryWorkflowMapping.SOURCE_SERVER)),
                null);
    }

    private UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>> failure(
            ApiStatus status, WorkflowNextStep nextStep, List<ApiError> errors,
            HttpStatusClassificationOverride override) {
        return UsernameRecoveryWorkflowMapping.policy(
                UsernameRecoveryWorkflowMapping.failureResponse(status, nextStep, errors), override);
    }
}