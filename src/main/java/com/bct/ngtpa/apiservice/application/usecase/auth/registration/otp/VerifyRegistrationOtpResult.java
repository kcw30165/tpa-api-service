package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Objects;

public record VerifyRegistrationOtpResult(
        VerifyRegistrationOtpResultStatus status,
        WorkflowNextStep nextStep,
        List<RegistrationUnregisteredTrust> unregisteredTrusts,
        Integer remainingVerifyAttempts,
        List<WorkflowMessageDescriptor> messages,
        List<WorkflowErrorDescriptor> errors,
        RegistrationIdempotencyStartOutcome idempotencyOutcome,
        RegistrationIdempotencyResponseSnapshot replaySnapshot) {
    public VerifyRegistrationOtpResult {
        Objects.requireNonNull(status, "status must not be null");
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
        unregisteredTrusts = unregisteredTrusts == null ? List.of() : List.copyOf(unregisteredTrusts);
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    @Override
    public String toString() {
        return "VerifyRegistrationOtpResult[status=" + status + ", nextStep=" + nextStep
                + ", trustCount=" + unregisteredTrusts.size() + ", remainingVerifyAttempts=" + remainingVerifyAttempts
                + ", messageCount=" + messages.size() + ", errorCount=" + errors.size() + "]";
    }
}
