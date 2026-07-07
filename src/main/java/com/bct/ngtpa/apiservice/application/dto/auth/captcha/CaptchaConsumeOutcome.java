package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

public enum CaptchaConsumeOutcome {
    CONSUMED,
    NOT_FOUND,
    NOT_VERIFIED,
    EXPIRED,
    LOCKED,
    ALREADY_CONSUMED,
    REFRESHED
}