package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.util.Objects;

public record LoginOtpSessionBindingCommand(
    String authFlowSessionId,
    String expectedCurrentOtpChallengeId,
    String newOtpChallengeId,
    AuthFlowStatus expectedStatus,
    AuthFlowStatus resultingStatus,
    String selectedContactType,
    String selectedMaskedDestination,
    String correlationId) {

  public LoginOtpSessionBindingCommand {
    Objects.requireNonNull(authFlowSessionId);
    Objects.requireNonNull(newOtpChallengeId);
    Objects.requireNonNull(expectedStatus);
    Objects.requireNonNull(resultingStatus);
    if ((selectedContactType == null) != (selectedMaskedDestination == null)) {
      throw new IllegalArgumentException(
          "Selected login OTP contact type and masked destination must be supplied together");
    }
  }

  public boolean hasSelectedDestination() {
    return selectedContactType != null
        && !selectedContactType.isBlank()
        && selectedMaskedDestination != null
        && !selectedMaskedDestination.isBlank();
  }

  @Override
  public String toString() {
    return "LoginOtpSessionBindingCommand[authFlowSessionId=<redacted>, "
        + "expectedCurrentOtpChallengeId=<redacted>, newOtpChallengeId=<redacted>, "
        + "expectedStatus="
        + expectedStatus
        + ", resultingStatus="
        + resultingStatus
        + ", selectedContactType=<redacted>, selectedMaskedDestination=<redacted>, "
        + "correlationId="
        + correlationId
        + "]";
  }
}
