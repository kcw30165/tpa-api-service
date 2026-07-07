package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

public enum CaptchaVerificationOutcome {
    VERIFIED,
    INVALID,
    EXPIRED,
    LOCKED,
    CONSUMED,
    REFRESHED,
    NOT_FOUND
}