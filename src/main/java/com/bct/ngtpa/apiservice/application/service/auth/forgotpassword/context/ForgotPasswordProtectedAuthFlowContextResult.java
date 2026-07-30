package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;

public record ForgotPasswordProtectedAuthFlowContextResult(
        ForgotPasswordProtectedAuthFlowContextStatus status,
        String activeOtpChallengeId,
        ForgotPasswordAuthFlowContext context) {

    public static ForgotPasswordProtectedAuthFlowContextResult valid(
            String activeOtpChallengeId,
            ForgotPasswordAuthFlowContext context) {
        return new ForgotPasswordProtectedAuthFlowContextResult(
                ForgotPasswordProtectedAuthFlowContextStatus.VALID,
                activeOtpChallengeId,
                context);
    }

    public static ForgotPasswordProtectedAuthFlowContextResult invalid(
            ForgotPasswordProtectedAuthFlowContextStatus status) {
        return new ForgotPasswordProtectedAuthFlowContextResult(status, null, null);
    }

    public boolean isValid() {
        return status == ForgotPasswordProtectedAuthFlowContextStatus.VALID;
    }

    @Override
    public String toString() {
        return "ForgotPasswordProtectedAuthFlowContextResult[status=" + status
                + ", activeOtpChallengeId=<redacted>, context=<redacted>]";
    }
}