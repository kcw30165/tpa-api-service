package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

public record RegistrationOtpCreationResult(String activeOtpChallengeId, String otpCode, int expiresInSeconds, int resendCooldownSeconds, int remainingResendAttempts) {
    @Override public String toString() { return "RegistrationOtpCreationResult[activeOtpChallengeId=<redacted>, otpCode=<redacted>, expiresInSeconds=" + expiresInSeconds + ", resendCooldownSeconds=" + resendCooldownSeconds + ", remainingResendAttempts=" + remainingResendAttempts + "]"; }
}
