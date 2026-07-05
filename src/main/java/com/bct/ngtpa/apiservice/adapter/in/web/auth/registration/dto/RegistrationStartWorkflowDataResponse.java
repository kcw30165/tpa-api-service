package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

/**
 * Endpoint-specific workflow data for registration/start.
 *
 * <p>WorkflowResultPayload already carries nextStep at result.nextStep, so this
 * DTO intentionally excludes nextStep to avoid duplicated nextStep fields in
 * the JSON response.</p>
 */
public record RegistrationStartWorkflowDataResponse(
        String maskedDestination,
        Integer expiresInSeconds,
        Integer resendCooldownSeconds,
        Integer remainingResendAttempts,
        String csrfToken) {

    @Override
    public String toString() {
        return "RegistrationStartWorkflowDataResponse[maskedDestination=" + maskedDestination
                + ", expiresInSeconds=" + expiresInSeconds
                + ", resendCooldownSeconds=" + resendCooldownSeconds
                + ", remainingResendAttempts=" + remainingResendAttempts
                + ", csrfToken=<redacted>]";
    }
}
