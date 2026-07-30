package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public enum ForgotPasswordIdempotencyAction {
    START,
    DETAILS_AND_OTP,
    OTP_RESEND,
    OTP_VERIFY,
    COMPLETE,
    CANCEL
}