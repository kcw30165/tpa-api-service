package com.bct.ngtpa.apiservice.application.dto.auth.otp;

public enum OtpChallengeInvalidationReason {
    USER_CANCELLED_REGISTRATION,
    USER_CANCELLED_LOGIN,
    USER_CANCELLED_FORGOT_PASSWORD,
    DELIVERY_FAILED
}
