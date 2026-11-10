package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public record InvestmentChoiceInstructionValidationResult(
    InvestmentChoiceInstructionRequest request, List<InvestmentChoiceViolation> violations) {

  public InvestmentChoiceInstructionValidationResult {
    violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
    if ((request == null) == violations.isEmpty()) {
      throw new IllegalArgumentException("result must contain either a request or violations");
    }
  }

  public boolean isValid() {
    return request != null;
  }

  @Override
  public String toString() {
    return "InvestmentChoiceInstructionValidationResult[<redacted>]";
  }
}
