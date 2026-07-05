package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public enum RegistrationOtpVerificationStatus {
    VERIFIED,
    INVALID,
    EXPIRED,
    LOCKED,
    ALREADY_VERIFIED
}
