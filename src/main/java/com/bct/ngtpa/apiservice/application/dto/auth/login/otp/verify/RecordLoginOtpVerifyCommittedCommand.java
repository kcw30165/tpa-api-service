package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyKey;
import java.util.Objects;

public record RecordLoginOtpVerifyCommittedCommand(LoginIdempotencyKey publicKey, String requestHash,
        LoginOtpConsumptionProof proof, LoginOtpVerifyPersistenceOutcome outcome, Integer remainingAttempts) {
    public RecordLoginOtpVerifyCommittedCommand {
        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(requestHash);
        Objects.requireNonNull(outcome);
        if (outcome == LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED && proof == null)
            throw new IllegalArgumentException("consumption proof required");
    }

    @Override
    public String toString() {
        return "RecordLoginOtpVerifyCommittedCommand[<redacted>]";
    }
}
