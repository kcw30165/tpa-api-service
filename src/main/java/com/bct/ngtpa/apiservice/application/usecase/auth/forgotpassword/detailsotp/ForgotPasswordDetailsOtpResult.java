package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import java.util.List;

public record ForgotPasswordDetailsOtpResult(Status status, String maskedDestination, int expiresInSeconds,
        int resendAvailableInSeconds, List<String> targets) {
    public enum Status {
        SUCCESS, EXPIRED, VALIDATION_FAILED, BUSINESS_REJECTED, SYSTEM_ERROR, IN_PROGRESS, KEY_REUSED
    }

    public static ForgotPasswordDetailsOtpResult system() {
        return new ForgotPasswordDetailsOtpResult(Status.SYSTEM_ERROR, null, 0, 0, List.of());
    }
}
