package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

public enum ResendLoginOtpStatus {
    RESENT, AUTH_FLOW_INVALID, COOLDOWN_ACTIVE, EXPIRED, LOCKED, NOT_REPLACEABLE, DELIVERY_FAILED, SYSTEM_FAILED
}
