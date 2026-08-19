package com.bct.ngtpa.apiservice.application.dto.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import java.util.Objects;

public record RegistrationAuthFlowContext(
    MemberIdentityType identityType,
    String identityValue,
    String dateOfBirth,
    RegistrationContactType contactType,
    String contactValue) {

  public RegistrationAuthFlowContext {
    Objects.requireNonNull(identityType, "identityType must not be null");
    identityValue = requireText(identityValue, "identityValue");
    dateOfBirth = requireText(dateOfBirth, "dateOfBirth");
    Objects.requireNonNull(contactType, "contactType must not be null");
    contactValue = requireText(contactValue, "contactValue");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  @Override
  public String toString() {
    return "RegistrationAuthFlowContext[identityType="
        + identityType
        + ", identityValue=<redacted>, dateOfBirth=<redacted>, contactType="
        + contactType
        + ", contactValue=<redacted>]";
  }
}
