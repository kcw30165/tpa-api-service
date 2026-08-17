package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

public record ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus status, String accountEnv) {
    public ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus status) {
        this(status, null);
    }
}
