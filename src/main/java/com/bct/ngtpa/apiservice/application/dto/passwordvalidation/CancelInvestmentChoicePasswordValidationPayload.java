package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record CancelInvestmentChoicePasswordValidationPayload(
    JsonKind rootKind, List<String> rootMembers, JsonKind refNoKind, String refNoValue)
    implements PasswordValidationPurposePayload {

  public CancelInvestmentChoicePasswordValidationPayload {
    Objects.requireNonNull(rootKind, "rootKind must not be null");
    rootMembers = List.copyOf(Objects.requireNonNull(rootMembers, "rootMembers must not be null"));
    Objects.requireNonNull(refNoKind, "refNoKind must not be null");
    if (refNoKind == JsonKind.MISSING || refNoKind == JsonKind.NULL) {
      if (refNoValue != null) {
        throw new IllegalArgumentException("refNoValue must be null for missing or null refNo");
      }
    } else {
      Objects.requireNonNull(refNoValue, "refNoValue must not be null for a present refNo");
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
