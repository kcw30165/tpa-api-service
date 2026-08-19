package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUnregisteredTrustResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerifyErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import java.util.List;
import java.util.Objects;

public class RegistrationOtpVerifyWebMapper {

  private static final String SOURCE_SERVER = "SERVER";
  private static final String TARGET_PAGE = "PAGE";
  private static final String TARGET_OTP_CODE = "otpCode";

  private final RegistrationWorkflowMutationResponseMapper workflowMutationResponseMapper;

  public RegistrationOtpVerifyWebMapper(
      com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper
          workflowMutationResponseMapper) {
    this.workflowMutationResponseMapper =
        new RegistrationWorkflowMutationResponseMapper(
            Objects.requireNonNull(
                workflowMutationResponseMapper, "workflowMutationResponseMapper must not be null"));
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
      toMutationResponse(VerifyRegistrationOtpResult result) {
    Objects.requireNonNull(result, "result must not be null");
    var response = workflowMutationResponseMapper.toMutationResponse(toWorkflowResult(result));
    if (result.status() == VerifyRegistrationOtpResultStatus.TRUST_LOOKUP_FAILED) {
      return new MutationResponse<>(
          false, ApiStatus.SYSTEM_ERROR, response.result(), response.messages(), response.errors());
    }
    return response;
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
      missingAuthFlowCookieResponse() {
    return validationFailure(
        RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID, TARGET_PAGE);
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
      missingCsrfTokenResponse() {
    return validationFailure(RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID, TARGET_PAGE);
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
      missingIdempotencyKeyResponse() {
    return validationFailure(
        com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.IDEMPOTENCY_MISSING
            .value(),
        "Idempotency-Key");
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
      missingCodeResponse() {
    return validationFailure(
        RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID, TARGET_OTP_CODE);
  }

  private WorkflowResult<RegistrationOtpVerifyResponse> toWorkflowResult(
      VerifyRegistrationOtpResult result) {
    return new WorkflowResult<>(
        workflowStatus(result.status()),
        result.nextStep(),
        data(result),
        List.of(),
        errors(result));
  }

  private WorkflowResultStatus workflowStatus(VerifyRegistrationOtpResultStatus status) {
    return switch (status) {
      case VERIFIED, COMPLETED_REPLAY -> WorkflowResultStatus.SUCCESS;
      case DUPLICATE_IN_PROGRESS -> WorkflowResultStatus.WAITING;
      case OTP_EXPIRED, OTP_LOCKED, NO_ELIGIBLE_TRUSTS -> WorkflowResultStatus.BUSINESS_FAILED;
      case TRUST_LOOKUP_FAILED, SYSTEM_FAILED -> WorkflowResultStatus.SYSTEM_FAILED;
      default -> WorkflowResultStatus.VALIDATION_FAILED;
    };
  }

  private RegistrationOtpVerifyResponse data(VerifyRegistrationOtpResult result) {
    if (result.status() == VerifyRegistrationOtpResultStatus.VERIFIED) {
      return new RegistrationOtpVerifyResponse(
          result.unregisteredTrusts().stream()
              .map(
                  trust ->
                      new RegistrationUnregisteredTrustResponse(
                          trust.accountEnv(), trust.trustCode()))
              .toList(),
          null);
    }
    if (exposeFailurePayload(result.status())) {
      return new RegistrationOtpVerifyResponse(List.of(), result.remainingVerifyAttempts());
    }
    return null;
  }

  private boolean exposeFailurePayload(VerifyRegistrationOtpResultStatus status) {
    return status == VerifyRegistrationOtpResultStatus.OTP_INVALID
        || status == VerifyRegistrationOtpResultStatus.OTP_EXPIRED
        || status == VerifyRegistrationOtpResultStatus.OTP_LOCKED;
  }

  private List<WorkflowErrorDescriptor> errors(VerifyRegistrationOtpResult result) {
    if (result.errors() != null && !result.errors().isEmpty()) {
      return result.errors().stream()
          .map(
              error ->
                  new WorkflowErrorDescriptor(
                      error.type(),
                      error.code(),
                      null,
                      error.targets(),
                      error.severity(),
                      error.source()))
          .toList();
    }
    return List.of(
        new WorkflowErrorDescriptor(
            defaultErrorType(result.status()),
            defaultErrorCode(result.status()),
            null,
            List.of(defaultTarget(result.status())),
            WorkflowErrorSeverity.ERROR,
            SOURCE_SERVER));
  }

  private WorkflowErrorType defaultErrorType(VerifyRegistrationOtpResultStatus status) {
    return switch (status) {
      case OTP_INVALID -> WorkflowErrorType.FIELD;
      case OTP_EXPIRED, OTP_LOCKED, NO_ELIGIBLE_TRUSTS -> WorkflowErrorType.BUSINESS;
      case TRUST_LOOKUP_FAILED -> WorkflowErrorType.DOWNSTREAM_SYSTEM;
      case SYSTEM_FAILED -> WorkflowErrorType.SYSTEM;
      default -> WorkflowErrorType.FORM;
    };
  }

  private String defaultTarget(VerifyRegistrationOtpResultStatus status) {
    return status == VerifyRegistrationOtpResultStatus.OTP_INVALID ? TARGET_OTP_CODE : TARGET_PAGE;
  }

  private String defaultErrorCode(VerifyRegistrationOtpResultStatus status) {
    return switch (status) {
      case INVALID_AUTH_FLOW -> RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID;
      case CSRF_INVALID -> RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID;
      case IDEMPOTENCY_KEY_REUSED -> RegistrationOtpVerifyErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED;
      case OTP_EXPIRED -> RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_EXPIRED;
      case OTP_LOCKED -> RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED;
      case NO_ELIGIBLE_TRUSTS -> RegistrationOtpVerifyErrorKeys.REGISTRATION_NO_ELIGIBLE_TRUSTS;
      case TRUST_LOOKUP_FAILED -> RegistrationOtpVerifyErrorKeys.REGISTRATION_TRUST_LOOKUP_FAILED;
      default -> RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID;
    };
  }

  private MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
      validationFailure(String code, String target) {
    WorkflowErrorDescriptor error =
        new WorkflowErrorDescriptor(
            TARGET_OTP_CODE.equals(target) ? WorkflowErrorType.FIELD : WorkflowErrorType.FORM,
            code,
            null,
            List.of(target),
            WorkflowErrorSeverity.ERROR,
            SOURCE_SERVER);
    return workflowMutationResponseMapper.toMutationResponse(
        new WorkflowResult<>(
            WorkflowResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.VERIFY_OTP,
            null,
            List.of(),
            List.of(error)));
  }
}
