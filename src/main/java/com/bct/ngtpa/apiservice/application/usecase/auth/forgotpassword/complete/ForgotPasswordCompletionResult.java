package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

public record ForgotPasswordCompletionResult(Status status, String downstreamErrorCode, String updateDate,
        String updateTime) {
    public enum Status {
        RESET,
        REJECTED,
        TECHNICAL_FAILURE,
        UNKNOWN_OUTCOME
    }

    public static ForgotPasswordCompletionResult reset(String updateDate, String updateTime) {
        return new ForgotPasswordCompletionResult(Status.RESET, null, updateDate, updateTime);
    }

    public static ForgotPasswordCompletionResult rejected(String code) {
        return new ForgotPasswordCompletionResult(Status.REJECTED, code, null, null);
    }

    public static ForgotPasswordCompletionResult technicalFailure() {
        return new ForgotPasswordCompletionResult(Status.TECHNICAL_FAILURE, null, null, null);
    }

    public static ForgotPasswordCompletionResult unknownOutcome() {
        return new ForgotPasswordCompletionResult(Status.UNKNOWN_OUTCOME, null, null, null);
    }
}