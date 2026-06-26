package com.bct.ngtpa.apiservice.application.dto.auth.otp;

public enum OtpReplacementOutcome {
    REPLACED,
    NOT_FOUND,
    COOLDOWN_ACTIVE,
    MAX_RESEND_EXCEEDED,
    EXPIRED,
    LOCKED,
    INVALIDATED,
    ALREADY_VERIFIED,
    NOT_REPLACEABLE
}