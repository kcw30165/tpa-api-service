package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.*;
import java.util.List;

public final class ForgotPasswordDetailsOtpWebMapper {
    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>> map(
            ForgotPasswordDetailsOtpResult r) {
        if (r.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS)
            return MutationResponse.success(ApiStatus.SUCCESS,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP,
                            new ForgotPasswordOtpDataResponse(r.maskedDestination(), r.expiresInSeconds(),
                                    r.resendAvailableInSeconds())),
                    List.of());
        if (r.status() == ForgotPasswordDetailsOtpResult.Status.EXPIRED)
            return MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.RESEND_OTP, null),
                    List.of(new ApiError("FORM", "err.forgot-password.otp.expired", null, List.of("PAGE"), "ERROR",
                            "BFF")));
        if (r.status() == ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED)
            return MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                    List.of(new ApiError("FORM", "err.forgot-password.details.invalid", null, r.targets(), "ERROR",
                            "BFF")));
        if (r.status() == ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED)
            return MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                    List.of(new ApiError("FORM", "err.tpa.member.not.found", null, List.of("PAGE"), "ERROR", "BFF")));
        return MutationResponse.failure(ApiStatus.SYSTEM_ERROR,
                List.of(new ApiError("SYSTEM", "err.system.unavailable", null, List.of("PAGE"), "ERROR", "BFF")));
    }
}
