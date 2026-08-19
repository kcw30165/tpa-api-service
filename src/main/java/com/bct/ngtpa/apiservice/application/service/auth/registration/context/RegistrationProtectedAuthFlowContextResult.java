package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;

public record RegistrationProtectedAuthFlowContextResult(
    RegistrationProtectedAuthFlowContextStatus status,
    String activeOtpChallengeId,
    String maskedDestination,
    RegistrationAuthFlowContext registrationContext) {

  public static RegistrationProtectedAuthFlowContextResult valid(
      String activeOtpChallengeId,
      String maskedDestination,
      RegistrationAuthFlowContext registrationContext) {
    return new RegistrationProtectedAuthFlowContextResult(
        RegistrationProtectedAuthFlowContextStatus.VALID,
        activeOtpChallengeId,
        maskedDestination,
        registrationContext);
  }

  public static RegistrationProtectedAuthFlowContextResult invalid(
      RegistrationProtectedAuthFlowContextStatus status) {
    return new RegistrationProtectedAuthFlowContextResult(status, null, null, null);
  }

  public boolean isValid() {
    return status == RegistrationProtectedAuthFlowContextStatus.VALID;
  }

  @Override
  public String toString() {
    return "RegistrationProtectedAuthFlowContextResult[status="
        + status
        + ", activeOtpChallengeId=<redacted>, maskedDestination="
        + maskedDestination
        + ", registrationContext=<redacted>]";
  }
}
