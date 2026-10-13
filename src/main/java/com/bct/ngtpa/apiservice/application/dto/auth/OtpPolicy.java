package com.bct.ngtpa.apiservice.application.dto.auth;

public record OtpPolicy(
    int length,
    boolean numericOnly,
    int ttlSeconds,
    int resendCooldownSeconds,
    int maxVerifyAttempts,
    boolean resendLimitEnabled,
    int maxResendAttempts,
    boolean invalidatePreviousOnResend,
    boolean resetVerifyAttemptsOnResend) {}
