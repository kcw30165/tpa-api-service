package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpSendDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.*;
import java.util.*;

public final class LoginOtpResendWebMapper {
    public MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>> toMutationResponse(
            ResendLoginOtpResult r) {
        boolean success = r.status() == ResendLoginOtpStatus.RESENT
                || r.status() == ResendLoginOtpStatus.COMPLETED_REPLAY;
        if (success)
            return MutationResponse.success(ApiStatus.SUCCESS, payload(r), List.of());
        String code = switch (r.status()) {
            case COOLDOWN_ACTIVE -> LoginErrorKey.OTP_COOLDOWN_ACTIVE.value();
            case EXPIRED -> LoginErrorKey.OTP_EXPIRED.value();
            case LOCKED -> LoginErrorKey.OTP_VERIFY_ATTEMPTS_EXCEEDED.value();
            case DELIVERY_FAILED -> LoginErrorKey.OTP_DELIVERY_FAILED.value();
            case DUPLICATE_IN_PROGRESS -> SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value();
            case KEY_REUSED -> SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value();
            case CSRF_INVALID -> LoginErrorKey.CSRF_INVALID.value();
            case AUTH_FLOW_INVALID -> LoginErrorKey.AUTH_FLOW_INVALID.value();
            default -> SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value();
        };
        ApiStatus status = r.status() == ResendLoginOtpStatus.CSRF_INVALID ? ApiStatus.VALIDATION_FAILED
                : r.status() == ResendLoginOtpStatus.DELIVERY_FAILED ? ApiStatus.SYSTEM_ERROR
                        : r.status() == ResendLoginOtpStatus.UNKNOWN_OUTCOME
                                || r.status() == ResendLoginOtpStatus.SYSTEM_FAILED ? ApiStatus.SYSTEM_ERROR
                                        : ApiStatus.BUSINESS_REJECTED;
        return MutationResponse.failure(status, payload(r), List.of(ApiError.form(code, null, List.of("PAGE"),
                r.status() == ResendLoginOtpStatus.CSRF_INVALID ? "SERVER" : "BFF")));
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>> missing(String code, String target) {
        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                new LoginWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP, null),
                List.of(ApiError.form(code, null, List.of(target), "BFF")));
    }

    private LoginWorkflowResultResponse<LoginOtpSendDataResponse> payload(ResendLoginOtpResult r) {
        return new LoginWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP, new LoginOtpSendDataResponse(
                r.maskedDestination(), r.expiresInSeconds(), r.resendCooldownSeconds(), r.retryAfterSeconds()));
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>> csrfInvalid() {
        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                new LoginWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP, null),
                List.of(ApiError.form(LoginErrorKey.CSRF_INVALID.value(), null, List.of("PAGE"), "SERVER")));
    }
}
