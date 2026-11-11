package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public record InvestmentChoiceCancelValidationResult(
    String referenceNo, List<InvestmentChoiceViolation> violations) {

  public InvestmentChoiceCancelValidationResult {
    violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
    if ((referenceNo == null) == violations.isEmpty()) {
      throw new IllegalArgumentException("result must contain either a reference or violations");
    }
  }

  public boolean isValid() {
    return referenceNo != null;
  }

  @Override
  public String toString() {
    return "InvestmentChoiceCancelValidationResult[<redacted>]";
  }
}
