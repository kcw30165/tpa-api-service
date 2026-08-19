package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public record ForgotPasswordOtpResendPolicyResult(
    ForgotPasswordOtpResendPolicyStatus status, int retryAfterSeconds) {}
