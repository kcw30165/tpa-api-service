package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

/**
 * Registration-start endpoint data carried inside WorkflowResultPayload.data. WorkflowResultPayload
 * already carries nextStep, so this DTO intentionally does not expose a nextStep property.
 */
public record RegistrationStartWorkflowDataResponse(
    String maskedDestination,
    Integer expiresInSeconds,
    Integer resendCooldownSeconds,
    Integer remainingResendAttempts,
    String csrfToken) {

  @Override
  public String toString() {
    return "RegistrationStartWorkflowDataResponse[maskedDestination="
        + maskedDestination
        + ", expiresInSeconds="
        + expiresInSeconds
        + ", resendCooldownSeconds="
        + resendCooldownSeconds
        + ", remainingResendAttempts="
        + remainingResendAttempts
        + ", csrfToken=<redacted>]";
  }
}
