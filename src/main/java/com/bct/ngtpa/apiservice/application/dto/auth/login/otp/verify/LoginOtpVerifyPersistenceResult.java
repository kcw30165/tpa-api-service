package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

public record LoginOtpVerifyPersistenceResult(LoginOtpVerifyPersistenceOutcome outcome, Integer remainingAttempts,
        LoginOtpConsumptionProof proof) {
    public LoginOtpVerifyPersistenceResult {
        if (outcome == null)
            throw new IllegalArgumentException("outcome required");
    }
}
