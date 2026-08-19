package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import java.util.Objects;

public record RegistrationStartCommand(
    MemberIdentityType identityType,
    String identityValue,
    String dateOfBirth,
    RegistrationContactType contactType,
    String contactValue,
    String maskedDestination,
    String captchaChallengeId) {
  public RegistrationStartCommand {
    Objects.requireNonNull(identityType, "identityType must not be null");
    Objects.requireNonNull(identityValue, "identityValue must not be null");
    Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
    Objects.requireNonNull(contactType, "contactType must not be null");
    Objects.requireNonNull(contactValue, "contactValue must not be null");
    Objects.requireNonNull(maskedDestination, "maskedDestination must not be null");
    if (identityValue.isBlank()
        || dateOfBirth.isBlank()
        || contactValue.isBlank()
        || maskedDestination.isBlank()) {
      throw new IllegalArgumentException("registration start command fields must not be blank");
    }
    captchaChallengeId =
        captchaChallengeId == null || captchaChallengeId.isBlank()
            ? null
            : captchaChallengeId.trim();
  }

  @Override
  public String toString() {
    return "RegistrationStartCommand[identityType="
        + identityType
        + ", identityValue=<redacted>, dateOfBirth=<redacted>, contactType="
        + contactType
        + ", contactValue=<redacted>, maskedDestination="
        + maskedDestination
        + ", captchaChallengeId=<redacted>]";
  }
}
