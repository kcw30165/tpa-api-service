package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record CancelInvestmentChoicePasswordValidationPayload(
    JsonKind rootKind, List<String> rootMembers, JsonKind referenceNoKind, String referenceNoValue)
    implements PasswordValidationPurposePayload {

  public CancelInvestmentChoicePasswordValidationPayload {
    Objects.requireNonNull(rootKind, "rootKind must not be null");
    rootMembers = List.copyOf(Objects.requireNonNull(rootMembers, "rootMembers must not be null"));
    Objects.requireNonNull(referenceNoKind, "referenceNoKind must not be null");
    if (referenceNoKind == JsonKind.MISSING || referenceNoKind == JsonKind.NULL) {
      if (referenceNoValue != null) {
        throw new IllegalArgumentException(
            "referenceNoValue must be null for missing or null referenceNo");
      }
    } else {
      Objects.requireNonNull(
          referenceNoValue, "referenceNoValue must not be null for a present referenceNo");
    }
  }

  public enum JsonKind {
    MISSING,
    NULL,
    OBJECT,
    ARRAY,
    STRING,
    BOOLEAN,
    NUMBER,
    OTHER
  }

  @Override
  public String toString() {
    return "CancelInvestmentChoicePasswordValidationPayload[<redacted>]";
  }
}
