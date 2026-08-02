package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import java.util.Objects;

public record RecoverCommittedLoginCommand(
        LoginOtpConsumptionProof proof,
        String correlationId) {
    public RecoverCommittedLoginCommand {
        Objects.requireNonNull(proof);
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }
}
