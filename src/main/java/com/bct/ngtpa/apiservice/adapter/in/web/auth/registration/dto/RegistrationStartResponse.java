package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record RegistrationStartResponse(WorkflowNextStep nextStep, String maskedDestination, Integer expiresInSeconds, Integer resendCooldownSeconds, Integer remainingResendAttempts, String csrfToken) {
    @Override public String toString() { return "RegistrationStartResponse[nextStep=" + nextStep + ", maskedDestination=" + maskedDestination + ", expiresInSeconds=" + expiresInSeconds + ", resendCooldownSeconds=" + resendCooldownSeconds + ", remainingResendAttempts=" + remainingResendAttempts + ", csrfToken=<redacted>]"; }
}
