package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordOtpDataResponse(String maskedDestination, int expiresInSeconds,
        int resendAvailableInSeconds) {
}
