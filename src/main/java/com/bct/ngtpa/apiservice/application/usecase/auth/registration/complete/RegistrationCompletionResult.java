package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

public record RegistrationCompletionResult(
        Status status,
        String downstreamErrorCode,
        String createDate,
        String createTime) {
    public enum Status {
        REGISTERED, REJECTED
    }

    public static RegistrationCompletionResult registered(String createDate, String createTime) {
        return new RegistrationCompletionResult(Status.REGISTERED, null, createDate, createTime);
    }

    public static RegistrationCompletionResult rejected(String code) {
        return new RegistrationCompletionResult(Status.REJECTED, code, null, null);
    }
}
