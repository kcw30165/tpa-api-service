package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.*;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public final class ForgotPasswordDetailsOtpWebMapper {
        public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>> map(
                        ForgotPasswordDetailsOtpResult r) {
                log.info("Mapping ForgotPasswordDetailsOtpResult: {}", r.toString());
                if (r.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS)
                        return MutationResponse.success(ApiStatus.SUCCESS,
                                        new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP,
                                                        new ForgotPasswordOtpDataResponse(r.maskedDestination(),
                                                                        r.expiresInSeconds(),
                                                                        r.resendAvailableInSeconds(),
                                                                        null,
                                                                        null)),
                                        List.of());
                if (r.status() == ForgotPasswordDetailsOtpResult.Status.EXPIRED)
                        return MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                                        new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.RESEND_OTP, null),
                                        List.of(new ApiError("FORM", "err.forgot-password.otp.expired", null, List.of(),
                                                        "ERROR",
                                                        "BFF")));
                if (r.status() == ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED)
                        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                                        List.of(new ApiError("FORM", "err.forgot-password.details.invalid", null,
                                                        r.targets(), "ERROR",
                                                        "BFF")));
                if (r.status() == ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED)
                        return MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                                        List.of(new ApiError("FORM", "err.tpa.member.not.found", null, List.of(),
                                                        "ERROR", "BFF")));
                return MutationResponse.failure(ApiStatus.SYSTEM_ERROR,
                                List.of(new ApiError("SYSTEM", "err.system.unavailable", null, List.of(), "ERROR",
                                                "BFF")));
        }
}
