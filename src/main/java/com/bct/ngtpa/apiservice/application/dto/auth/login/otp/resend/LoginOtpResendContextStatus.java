package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend;

public enum LoginOtpResendContextStatus {
    VALID,
    MISSING_SESSION,
    EXPIRED_OR_TERMINAL,
    WRONG_SCENARIO,
    INVALID_STATUS,
    MISSING_ACTIVE_OTP,
    MISSING_CONTEXT,
    CONTEXT_DECRYPTION_FAILED,
    CSRF_INVALID,
    ORIGIN_REFERER_INVALID
}
