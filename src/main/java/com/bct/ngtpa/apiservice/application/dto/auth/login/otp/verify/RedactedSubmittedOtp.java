package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

public final class RedactedSubmittedOtp {
    private final String value;

    public RedactedSubmittedOtp(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("OTP required");
        this.value = value;
    }

    public String revealForComparison() {
        return value;
    }

    @Override
    public String toString() {
        return "<redacted>";
    }
}
