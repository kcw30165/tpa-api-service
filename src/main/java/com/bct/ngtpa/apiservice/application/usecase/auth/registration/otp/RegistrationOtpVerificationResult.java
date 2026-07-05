package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public record RegistrationOtpVerificationResult(RegistrationOtpVerificationStatus status, Integer remainingVerifyAttempts) {
    public static RegistrationOtpVerificationResult verified() { return new RegistrationOtpVerificationResult(RegistrationOtpVerificationStatus.VERIFIED, null); }
    public static RegistrationOtpVerificationResult invalid(Integer remaining) { return new RegistrationOtpVerificationResult(RegistrationOtpVerificationStatus.INVALID, remaining); }
    public static RegistrationOtpVerificationResult expired() { return new RegistrationOtpVerificationResult(RegistrationOtpVerificationStatus.EXPIRED, null); }
    public static RegistrationOtpVerificationResult locked() { return new RegistrationOtpVerificationResult(RegistrationOtpVerificationStatus.LOCKED, 0); }
}
