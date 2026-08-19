package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordOtpDataResponse(
    String maskedDestination,
    Integer expiresInSeconds,
    Integer resendCooldownSeconds,
    Integer remainingResendAttempts,
    Integer retryAfterSeconds) {}
