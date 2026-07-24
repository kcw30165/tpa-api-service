package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.otp;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import java.util.*;

public final class LoginOtpSendWebMapper {
    public MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>> toMutationResponse(
            SendLoginOtpResult r) {
        if (r.status() == SendLoginOtpStatus.SENT || r.status() == SendLoginOtpStatus.COMPLETED_REPLAY)
            return MutationResponse.success(ApiStatus.SUCCESS,
                    new LoginWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP,
                            new LoginOtpSendDataResponse(r.maskedDestination(), r.expiresInSeconds(),
                                    r.resendCooldownSeconds(), r.retryAfterSeconds())),
                    List.of());
        String code = switch (r.status()) {
            case AUTH_FLOW_INVALID -> LoginErrorKey.AUTH_FLOW_INVALID.value();
            case INVALID_DESTINATION -> LoginErrorKey.OTP_DESTINATION_INVALID.value();
            case DELIVERY_FAILED -> LoginErrorKey.OTP_DELIVERY_FAILED.value();
            case DUPLICATE_IN_PROGRESS -> SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value();
            case KEY_REUSED -> SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value();
            default -> SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value();
        };
        ApiStatus status = r.status() == SendLoginOtpStatus.DELIVERY_FAILED ? ApiStatus.DOWNSTREAM_ERROR
                : r.status() == SendLoginOtpStatus.UNKNOWN_OUTCOME ? ApiStatus.SYSTEM_ERROR
                        : ApiStatus.VALIDATION_FAILED;
        return MutationResponse.failure(status,
                new LoginWorkflowResultResponse<>(WorkflowNextStep.SELECT_OTP_DESTINATION, null),
                List.of(ApiError.form(code, null, List.of("PAGE"), "BFF")));
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>> missing(String code, String target) {
        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                new LoginWorkflowResultResponse<>(WorkflowNextStep.SELECT_OTP_DESTINATION, null),
                List.of(ApiError.form(code, null, List.of(target), "BFF")));
    }
}
