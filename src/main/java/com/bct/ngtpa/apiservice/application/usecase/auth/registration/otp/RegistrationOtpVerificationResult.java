package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;

public record RegistrationOtpVerificationResult(OtpStatus status, Integer remainingVerifyAttempts) {
    public static RegistrationOtpVerificationResult verified() {
        return new RegistrationOtpVerificationResult(OtpStatus.VERIFIED, null);
    }

    public static RegistrationOtpVerificationResult invalid(Integer remaining) {
        return new RegistrationOtpVerificationResult(OtpStatus.INVALIDATED, remaining);
    }

    public static RegistrationOtpVerificationResult expired() {
        return new RegistrationOtpVerificationResult(OtpStatus.EXPIRED, null);
    }

    public static RegistrationOtpVerificationResult locked() {
        return new RegistrationOtpVerificationResult(OtpStatus.LOCKED, 0);
    }
}
