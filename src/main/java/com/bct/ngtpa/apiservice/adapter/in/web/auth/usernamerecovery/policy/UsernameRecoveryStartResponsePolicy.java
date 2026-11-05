package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryWorkflowMapping;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.UsernameRecoveryErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Error;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Kind;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

/**
 * Typed Start response policy: maps every Start result outcome and every Start exception reason
 * into a completed {@code MutationResponse} plus an optional typed HTTP classification override,
 * reusing the Task 022 Start mapper projection as the only success data source.
 *
 * <p>This class never chooses an HTTP status, never reads headers/cookies, and never catches
 * arbitrary exceptions.
 */
public final class UsernameRecoveryStartResponsePolicy {

  public UsernameRecoveryResponsePolicy<
          UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
      toPolicy(
          UsernameRecoveryStartResult result, UsernameRecoveryStartWebMapper projectionMapper) {
    Objects.requireNonNull(result, "username recovery start result required");
    Objects.requireNonNull(projectionMapper, "username recovery start projection mapper required");
    return switch (result.outcome()) {
      case SESSION_CREATED ->
          UsernameRecoveryWorkflowMapping.policy(
              MutationResponse.success(
                  ApiStatus.SUCCESS, projectionMapper.toResult(result), List.of()));
      case MEMBER_NOT_FOUND ->
          UsernameRecoveryWorkflowMapping.policy(
              MutationResponse.failure(
                  ApiStatus.BUSINESS_REJECTED,
                  projectionMapper.toResult(result),
                  List.of(
                      UsernameRecoveryWorkflowMapping.formError(
                          UsernameRecoveryErrorKey.VERIFICATION_FAILED.value(),
                          List.of(),
                          UsernameRecoveryWorkflowMapping.SOURCE_BFF))));
    };
  }

  public UsernameRecoveryResponsePolicy<
          UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
      toPolicy(UsernameRecoveryStartException exception) {
    Objects.requireNonNull(exception, "username recovery start exception required");
    return switch (exception.reason()) {
      case VALIDATION_FAILED -> validationPolicy(exception.validationErrors());
      case IDEMPOTENCY_IN_PROGRESS ->
          failure(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.WAIT,
              List.of(
                  UsernameRecoveryWorkflowMapping.businessError(
                      SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
                      List.of(),
                      UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
              HttpStatusClassificationOverride.CONFLICT);
      case IDEMPOTENCY_KEY_REUSED ->
          failure(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              List.of(
                  UsernameRecoveryWorkflowMapping.formError(
                      SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                      List.of(UsernameRecoveryWorkflowMapping.TARGET_IDEMPOTENCY_KEY),
                      UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
              HttpStatusClassificationOverride.CONFLICT);
      case UNKNOWN_OUTCOME_EXISTING -> systemFailure(WorkflowNextStep.WAIT);
      case CAPTCHA_INVALID ->
          validationFailure(RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID.value());
      case CAPTCHA_NOT_FOUND ->
          validationFailure(RegistrationErrorKey.REGISTRATION_CAPTCHA_NOTFOUND.value());
      case CAPTCHA_NOT_VERIFIED ->
          validationFailure(RegistrationErrorKey.REGISTRATION_CAPTCHA_REQUIRED.value());
      case CAPTCHA_EXPIRED ->
          validationFailure(RegistrationErrorKey.REGISTRATION_CAPTCHA_EXPIRED.value());
      case CAPTCHA_CONFLICT ->
          failure(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.NONE,
              List.of(
                  UsernameRecoveryWorkflowMapping.businessError(
                      RegistrationErrorKey.REGISTRATION_CAPTCHA_CONSUMED.value(),
                      List.of(),
                      UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
              HttpStatusClassificationOverride.CONFLICT);
      case REPLAY_REVISION_CONFLICT ->
          failure(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.NONE,
              List.of(
                  UsernameRecoveryWorkflowMapping.businessError(
                      SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                      List.of(),
                      UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
              HttpStatusClassificationOverride.CONFLICT);
      case SYSTEM_FAILURE -> systemFailure(WorkflowNextStep.NONE);
    };
  }

  private UsernameRecoveryResponsePolicy<
          UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
      validationPolicy(List<Error> validationErrors) {
    List<ApiError> errors =
        validationErrors.isEmpty()
            ? List.of(
                UsernameRecoveryWorkflowMapping.formError(
                    ErrorCodes.REQUEST_VALIDATION_FAILED,
                    List.of(),
                    UsernameRecoveryWorkflowMapping.SOURCE_BFF))
            : validationErrors.stream()
                .map(
                    error ->
                        ApiError.field(
                            validationCode(error.kind()),
                            null,
                            List.of(error.target()),
                            UsernameRecoveryWorkflowMapping.SOURCE_BFF))
                .toList();
    return failure(ApiStatus.VALIDATION_FAILED, WorkflowNextStep.NONE, errors, null);
  }

  private UsernameRecoveryResponsePolicy<
          UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
      validationFailure(String code) {
    return failure(
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.NONE,
        List.of(
            UsernameRecoveryWorkflowMapping.formError(
                code, List.of(), UsernameRecoveryWorkflowMapping.SOURCE_BFF)),
        null);
  }

  private UsernameRecoveryResponsePolicy<
          UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
      systemFailure(WorkflowNextStep nextStep) {
    return failure(
        ApiStatus.SYSTEM_ERROR,
        nextStep,
        List.of(
            UsernameRecoveryWorkflowMapping.systemError(
                SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                UsernameRecoveryWorkflowMapping.SOURCE_SERVER)),
        null);
  }

  private UsernameRecoveryResponsePolicy<
          UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
      failure(
          ApiStatus status,
          WorkflowNextStep nextStep,
          List<ApiError> errors,
          HttpStatusClassificationOverride override) {
    return UsernameRecoveryWorkflowMapping.policy(
        UsernameRecoveryWorkflowMapping.failureResponse(status, nextStep, errors), override);
  }

  private static String validationCode(Kind kind) {
    return switch (kind) {
      case IDENTITY_VALUE_INVALID ->
          RegistrationErrorKey.REGISTRATION_IDENTITYVALUE_INVALID.value();
      case DATE_OF_BIRTH_INVALID -> RegistrationErrorKey.REGISTRATION_DATEOFBIRTH_INVALID.value();
      case CONTACT_REQUIRED -> RegistrationErrorKey.REGISTRATION_CONTACT_REQUIRED.value();
      case CONTACT_EXACTLY_ONE_REQUIRED ->
          RegistrationErrorKey.REGISTRATION_CONTACT_EXACTLYONEREQUIRED.value();
      case EMAIL_INVALID -> RegistrationErrorKey.REGISTRATION_EMAIL_INVALID.value();
      case MOBILE_INVALID -> RegistrationErrorKey.REGISTRATION_MOBILE_INVALID.value();
      case IDEMPOTENCY_KEY_INVALID -> ErrorCodes.REQUEST_VALIDATION_FAILED;
    };
  }
}
