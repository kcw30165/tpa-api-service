package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

public enum LoginIdempotencyAction {
    CREDENTIALS("credentials"),
    OTP_SEND("otp-send"),
    OTP_RESEND("otp-resend"),
    OTP_VERIFY("otp-verify");

    private final String keyNamespace;

    LoginIdempotencyAction(String value) {
        this.keyNamespace = value;
    }

    public String keyNamespace() {
        return keyNamespace;
    }
}
