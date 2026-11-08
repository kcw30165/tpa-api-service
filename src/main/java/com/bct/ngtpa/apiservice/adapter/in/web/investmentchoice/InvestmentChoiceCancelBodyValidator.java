package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class InvestmentChoiceCancelBodyValidator {

  private static final String REF_NO_MEMBER = "refNo";
  private static final Set<String> BODY_MEMBERS = Set.of(REF_NO_MEMBER);

  public ValidationResult validate(JsonNode body) {
    if (body == null || !body.isObject() || containsUnknownMember(body)) {
      return invalid(formViolation());
    }

    JsonNode reference = body.get(REF_NO_MEMBER);
    if (reference == null) {
      return invalid(requiredViolation());
    }
    if (!reference.isTextual()) {
      return invalid(formViolation());
    }
    String referenceNo = reference.textValue();
    if (referenceNo.isBlank()) {
      return invalid(requiredViolation());
    }
    return new ValidationResult(referenceNo, List.of());
  }

  private static boolean containsUnknownMember(JsonNode body) {
    var names = body.fieldNames();
    while (names.hasNext()) {
      if (!BODY_MEMBERS.contains(names.next())) {
        return true;
      }
    }
    return false;
  }

  private static InvestmentChoiceViolation formViolation() {
    return new InvestmentChoiceViolation(
        InvestmentChoiceViolation.Kind.FORM,
        ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
        List.of());
  }

  private static InvestmentChoiceViolation requiredViolation() {
    return new InvestmentChoiceViolation(
        InvestmentChoiceViolation.Kind.FIELD,
        ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
        List.of(REF_NO_MEMBER));
  }

  private static ValidationResult invalid(InvestmentChoiceViolation violation) {
    return new ValidationResult(
        null, List.of(Objects.requireNonNull(violation, "violation must not be null")));
  }

  public record ValidationResult(String referenceNo, List<InvestmentChoiceViolation> violations) {
    public ValidationResult {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
    }

    public boolean isValid() {
      return violations.isEmpty();
    }
  }
}
