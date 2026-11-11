package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelValidationResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.CancelInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

public final class InvestmentChoiceCancelPrecheckValidator {

  private static final String REFERENCE_NO = "referenceNo";

  public InvestmentChoiceCancelValidationResult validate(
      CancelInvestmentChoicePasswordValidationPayload payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    if (payload.rootKind() != JsonKind.OBJECT
        || hasUnknownMember(payload.rootMembers())
        || (payload.referenceNoKind() != JsonKind.MISSING
            && payload.referenceNoKind() != JsonKind.STRING)) {
      return invalid(
          new InvestmentChoiceViolation(
              InvestmentChoiceViolation.Kind.FORM,
              ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
              List.of()));
    }

    if (payload.referenceNoKind() == JsonKind.MISSING || payload.referenceNoValue().isBlank()) {
      return invalid(
          new InvestmentChoiceViolation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
              List.of(REFERENCE_NO)));
    }
    return valid(payload.referenceNoValue());
  }

  private static boolean hasUnknownMember(List<String> rootMembers) {
    return rootMembers.stream().anyMatch(member -> !REFERENCE_NO.equals(member));
  }

  private static InvestmentChoiceCancelValidationResult valid(String referenceNo) {
    return new InvestmentChoiceCancelValidationResult(referenceNo, List.of());
  }

  private static InvestmentChoiceCancelValidationResult invalid(
      InvestmentChoiceViolation violation) {
    return new InvestmentChoiceCancelValidationResult(null, List.of(violation));
  }
}
