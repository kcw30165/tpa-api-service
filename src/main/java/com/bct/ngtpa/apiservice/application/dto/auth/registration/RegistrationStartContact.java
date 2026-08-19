package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import java.util.Objects;

public record RegistrationStartContact(
    RegistrationContactType contactType, String contactValue, String maskedDestination) {
  public RegistrationStartContact {
    Objects.requireNonNull(contactType, "contactType must not be null");
    Objects.requireNonNull(contactValue, "contactValue must not be null");
    Objects.requireNonNull(maskedDestination, "maskedDestination must not be null");
    if (contactValue.isBlank()) {
      throw new IllegalArgumentException("contactValue must not be blank");
    }
    if (maskedDestination.isBlank()) {
      throw new IllegalArgumentException("maskedDestination must not be blank");
    }
  }

  @Override
  public String toString() {
    return "RegistrationStartContact[contactType="
        + contactType
        + ", contactValue=<redacted>, maskedDestination="
        + maskedDestination
        + "]";
  }
}
