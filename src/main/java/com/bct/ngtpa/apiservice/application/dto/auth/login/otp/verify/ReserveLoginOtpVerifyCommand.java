package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import java.time.*;
import java.util.Objects;

public record ReserveLoginOtpVerifyCommand(LoginIdempotencyKey publicKey, String requestHash,
        AuthenticatedLoginHandoffReference proposedHandoff, LoginOtpVerificationOperationReference proposedOperation,
        Instant now, Instant expiresAt) {
    public ReserveLoginOtpVerifyCommand {
        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(requestHash);
        Objects.requireNonNull(proposedHandoff);
        Objects.requireNonNull(proposedOperation);
        Objects.requireNonNull(now);
        Objects.requireNonNull(expiresAt);
    }

    @Override
    public String toString() {
        return "ReserveLoginOtpVerifyCommand[<redacted>]";
    }
}
