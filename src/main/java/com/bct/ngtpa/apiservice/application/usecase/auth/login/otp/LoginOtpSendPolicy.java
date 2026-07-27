package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

public record LoginOtpSendPolicy(int length, boolean numericOnly, int ttlSeconds, int resendCooldownSeconds,
        int maxVerifyAttempts, boolean resendLimitEnabled, int maxResendAttempts, String messageTemplate,
        String emailSubject) {
    public LoginOtpSendPolicy {
        if (length <= 0 || ttlSeconds <= 0 || resendCooldownSeconds < 0 || maxVerifyAttempts <= 0)
            throw new IllegalArgumentException("Invalid login OTP policy");
        if (messageTemplate == null || !messageTemplate.contains("{code}"))
            throw new IllegalArgumentException("OTP template must contain code placeholder");
    }
}
