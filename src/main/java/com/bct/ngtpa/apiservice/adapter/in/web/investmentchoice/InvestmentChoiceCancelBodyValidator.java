package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.CancelInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelValidationResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceCancelPrecheckValidator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

public final class InvestmentChoiceCancelBodyValidator {

  private final CancelInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new CancelInvestmentChoicePasswordValidationPayloadDecoder();
  private final InvestmentChoiceCancelPrecheckValidator validator =
      new InvestmentChoiceCancelPrecheckValidator();

  public ValidationResult validate(JsonNode body) {
    InvestmentChoiceCancelValidationResult result = validator.validate(decoder.decode(body));
    return new ValidationResult(result.referenceNo(), result.violations());
  }

  public record ValidationResult(String referenceNo, List<InvestmentChoiceViolation> violations) {
    public ValidationResult {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
    }

    public boolean isValid() {
      return violations.isEmpty();
    }

    @Override
    public String toString() {
      return "ValidationResult[<redacted>]";
    }
  }
}
