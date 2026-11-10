package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayloadDecoder;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceInstructionPrecheckValidator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class InvestmentChoiceInstructionBodyValidator {

  private final ChangeInvestmentChoicePasswordValidationPayloadDecoder decoder =
      new ChangeInvestmentChoicePasswordValidationPayloadDecoder();
  private final InvestmentChoiceInstructionPrecheckValidator validator =
      new InvestmentChoiceInstructionPrecheckValidator();

  public ValidationResult validate(JsonNode body) {
    var result = validator.validate(decoder.decode(body));
    return new ValidationResult(result.request(), result.violations());
  }

  public record ValidationResult(
      InvestmentChoiceInstructionRequest request, List<InvestmentChoiceViolation> violations) {

    public ValidationResult {
      violations = List.copyOf(violations);
      if ((request == null) == violations.isEmpty()) {
        throw new IllegalArgumentException("result must contain either a request or violations");
      }
    }

    public boolean isValid() {
      return request != null;
    }

    @Override
    public String toString() {
      return "ValidationResult[<redacted>]";
    }
  }
}
