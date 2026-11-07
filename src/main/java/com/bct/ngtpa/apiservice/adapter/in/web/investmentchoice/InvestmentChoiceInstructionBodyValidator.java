package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InvestmentChoiceInstructionBodyValidator {

  private static final Set<String> BODY_MEMBERS = Set.of("switchSources", "allocations");
  private static final Set<String> ALLOCATION_MEMBERS = Set.of("fundCode", "percentage");

  public ValidationResult validate(JsonNode body) {
    if (hasMalformedStructure(body)) {
      return invalid(
          List.of(
              new InvestmentChoiceViolation(
                  InvestmentChoiceViolation.Kind.FORM,
                  ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
                  List.of())));
    }

    JsonNode switchSources = body.get("switchSources");
    JsonNode allocations = body.get("allocations");
    List<InvestmentChoiceViolation> violations = new ArrayList<>();

    appendSwitchSourceViolations(switchSources, violations);
    appendAllocationViolations(allocations, violations);
    if (!violations.isEmpty()) {
      return invalid(violations);
    }
    return valid(toRequest(switchSources, allocations));
  }

  private boolean hasMalformedStructure(JsonNode body) {
    if (body == null || !body.isObject() || containsUnknownMember(body, BODY_MEMBERS)) {
      return true;
    }
    JsonNode switchSources = body.get("switchSources");
    if (switchSources != null && !switchSources.isArray()) {
      return true;
    }
    JsonNode allocations = body.get("allocations");
    if (allocations != null && !allocations.isArray()) {
      return true;
    }
    if (switchSources != null) {
      for (JsonNode source : switchSources) {
        if (!source.isTextual()) {
          return true;
        }
      }
    }
    if (allocations != null) {
      for (JsonNode allocation : allocations) {
        if (!allocation.isObject() || containsUnknownMember(allocation, ALLOCATION_MEMBERS)) {
          return true;
        }
        JsonNode fundCode = allocation.get("fundCode");
        if (fundCode != null && !fundCode.isTextual()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean containsUnknownMember(JsonNode object, Set<String> knownMembers) {
    var names = object.fieldNames();
    while (names.hasNext()) {
      if (!knownMembers.contains(names.next())) {
        return true;
      }
    }
    return false;
  }

  private void appendSwitchSourceViolations(
      JsonNode switchSources, List<InvestmentChoiceViolation> violations) {
    boolean required = switchSources == null || switchSources.isEmpty();
    List<InvestmentChoiceViolation> duplicates = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (switchSources != null) {
      for (int index = 0; index < switchSources.size(); index++) {
        String source = switchSources.get(index).textValue();
        if (source.isBlank()) {
          required = true;
        }
        if (!seen.add(source)) {
          duplicates.add(
              violation(
                  InvestmentChoiceViolation.Kind.FIELD,
                  ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                  "switchSources[" + index + "]"));
        }
      }
    }
    if (required) {
      violations.add(
          violation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
              "switchSources"));
    }
    violations.addAll(duplicates);
  }

  private void appendAllocationViolations(
      JsonNode allocations, List<InvestmentChoiceViolation> violations) {
    if (allocations == null || allocations.isEmpty()) {
      violations.add(
          violation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
              "allocations"));
      return;
    }

    List<InvestmentChoiceViolation> missingFunds = new ArrayList<>();
    List<InvestmentChoiceViolation> duplicateFunds = new ArrayList<>();
    List<InvestmentChoiceViolation> invalidPercentages = new ArrayList<>();
    Set<String> seenFunds = new HashSet<>();
    long percentageTotal = 0;
    boolean allPercentagesValid = true;
    for (int index = 0; index < allocations.size(); index++) {
      JsonNode allocation = allocations.get(index);
      JsonNode fundCodeNode = allocation.get("fundCode");
      if (fundCodeNode == null || fundCodeNode.textValue().isBlank()) {
        missingFunds.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                "allocations[" + index + "].fundCode"));
      } else if (!seenFunds.add(fundCodeNode.textValue())) {
        duplicateFunds.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                "allocations[" + index + "].fundCode"));
      }

      JsonNode percentage = allocation.get("percentage");
      if (!validPercentage(percentage)) {
        invalidPercentages.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                "allocations[" + index + "].percentage"));
        allPercentagesValid = false;
      } else {
        percentageTotal += percentage.intValue();
      }
    }
    violations.addAll(missingFunds);
    violations.addAll(duplicateFunds);
    violations.addAll(invalidPercentages);
    if (allPercentagesValid && percentageTotal != 100) {
      violations.add(
          violation(
              InvestmentChoiceViolation.Kind.CROSS_FIELD,
              ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
              "allocations"));
    }
  }

  private boolean validPercentage(JsonNode percentage) {
    return percentage != null
        && percentage.isIntegralNumber()
        && percentage.canConvertToInt()
        && percentage.intValue() >= 1
        && percentage.intValue() <= 100;
  }

  private InvestmentChoiceInstructionRequest toRequest(
      JsonNode switchSources, JsonNode allocations) {
    List<String> sources = new ArrayList<>(switchSources.size());
    for (JsonNode source : switchSources) {
      sources.add(source.textValue());
    }
    List<InvestmentChoiceInstructionRequest.Allocation> mappedAllocations =
        new ArrayList<>(allocations.size());
    for (JsonNode allocation : allocations) {
      mappedAllocations.add(
          new InvestmentChoiceInstructionRequest.Allocation(
              allocation.get("fundCode").textValue(), allocation.get("percentage").intValue()));
    }
    return new InvestmentChoiceInstructionRequest(sources, mappedAllocations);
  }

  private InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String target) {
    return new InvestmentChoiceViolation(kind, code, List.of(target));
  }

  private ValidationResult valid(InvestmentChoiceInstructionRequest request) {
    return new ValidationResult(request, List.of());
  }

  private ValidationResult invalid(List<InvestmentChoiceViolation> violations) {
    return new ValidationResult(null, violations);
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
  }
}
