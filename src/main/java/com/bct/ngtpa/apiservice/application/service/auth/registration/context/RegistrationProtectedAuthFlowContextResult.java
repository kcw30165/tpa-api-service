package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;

public record RegistrationProtectedAuthFlowContextResult(
    RegistrationProtectedAuthFlowContextStatus status,
    String activeOtpChallengeId,
    String maskedDestination,
    RegistrationAuthFlowContext registrationContext,
    AuthFlowSession authFlowSession) {

  public static RegistrationProtectedAuthFlowContextResult valid(
      String activeOtpChallengeId,
      String maskedDestination,
      RegistrationAuthFlowContext registrationContext) {
    return new RegistrationProtectedAuthFlowContextResult(
        RegistrationProtectedAuthFlowContextStatus.VALID,
        activeOtpChallengeId,
        maskedDestination,
        registrationContext,
        null);
  }

  public static RegistrationProtectedAuthFlowContextResult valid(
      AuthFlowSession authFlowSession,
      String activeOtpChallengeId,
      String maskedDestination,
      RegistrationAuthFlowContext registrationContext) {
    return new RegistrationProtectedAuthFlowContextResult(
        RegistrationProtectedAuthFlowContextStatus.VALID,
        activeOtpChallengeId,
        maskedDestination,
        registrationContext,
        authFlowSession);
  }

  public static RegistrationProtectedAuthFlowContextResult invalid(
      RegistrationProtectedAuthFlowContextStatus status) {
    return new RegistrationProtectedAuthFlowContextResult(status, null, null, null, null);
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
        + ", registrationContext=<redacted>, authFlowSession=<redacted>]";
  }
}
