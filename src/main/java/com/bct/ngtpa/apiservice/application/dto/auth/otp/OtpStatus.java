package com.bct.ngtpa.apiservice.application.dto.auth.otp;

public enum OtpStatus {
    CREATED,
    VERIFIED,
    CONSUMED,
    INVALIDATED,
    EXPIRED,
    LOCKED
}