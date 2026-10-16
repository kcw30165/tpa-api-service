package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ForgotPasswordDetailsOtpWebMapper {
  private static final String SEVERITY_ERROR = "ERROR";

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>> map(
      ForgotPasswordDetailsOtpResult r) {
    if (r.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS)
      return MutationResponse.success(
          ApiStatus.SUCCESS,
          new ForgotPasswordWorkflowResultResponse<>(
              WorkflowNextStep.VERIFY_OTP,
              new ForgotPasswordOtpDataResponse(
                  r.maskedDestination(),
                  r.expiresInSeconds(),
                  r.resendAvailableInSeconds(),
                  null,
                  null)),
          List.of());
    if (r.status() == ForgotPasswordDetailsOtpResult.Status.EXPIRED)
      return MutationResponse.failure(
          ApiStatus.BUSINESS_REJECTED,
          new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.RESEND_OTP, null),
          List.of(
              new ApiError(
                  "FORM",
                  "err.forgot-password.otp.expired",
                  null,
                  List.of(),
                  SEVERITY_ERROR,
                  "BFF")));
    if (r.status() == ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED)
      return MutationResponse.failure(
          ApiStatus.VALIDATION_FAILED,
          List.of(
              new ApiError(
                  "FORM",
                  "err.forgot-password.details.invalid",
                  null,
                  r.targets(),
                  SEVERITY_ERROR,
                  "BFF")));
    if (r.status() == ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED)
      return MutationResponse.failure(
          ApiStatus.BUSINESS_REJECTED,
          List.of(
              new ApiError(
                  "BUSINESS",
                  "err.forgot-password.member.notFound",
                  null,
                  List.of(),
                  SEVERITY_ERROR,
                  "BFF")));
    return MutationResponse.failure(
        ApiStatus.SYSTEM_ERROR,
        List.of(
            new ApiError(
                "SYSTEM", "err.system.unavailable", null, List.of(), SEVERITY_ERROR, "BFF")));
  }
}
