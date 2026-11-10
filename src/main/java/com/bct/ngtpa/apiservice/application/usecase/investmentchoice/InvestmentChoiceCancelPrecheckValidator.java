package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelValidationResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

public final class InvestmentChoiceCancelPrecheckValidator {

  private static final String REF_NO = "refNo";

  public InvestmentChoiceCancelValidationResult validate(
      CancelInvestmentChoicePasswordValidationPayload payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    if (payload.rootKind() != JsonKind.OBJECT
        || hasUnknownMember(payload.rootMembers())
        || (payload.refNoKind() != JsonKind.MISSING && payload.refNoKind() != JsonKind.STRING)) {
      return invalid(
          new InvestmentChoiceViolation(
              InvestmentChoiceViolation.Kind.FORM,
              ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
              List.of()));
    }

    if (payload.refNoKind() == JsonKind.MISSING || payload.refNoValue().isBlank()) {
      return invalid(
          new InvestmentChoiceViolation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
              List.of(REF_NO)));
    }
    return valid(payload.refNoValue());
  }

  private static boolean hasUnknownMember(List<String> rootMembers) {
    return rootMembers.stream().anyMatch(member -> !REF_NO.equals(member));
  }

  private static InvestmentChoiceCancelValidationResult valid(String referenceNo) {
    return new InvestmentChoiceCancelValidationResult(referenceNo, List.of());
  }

  private static InvestmentChoiceCancelValidationResult invalid(
      InvestmentChoiceViolation violation) {
    return new InvestmentChoiceCancelValidationResult(null, List.of(violation));
  }
}
