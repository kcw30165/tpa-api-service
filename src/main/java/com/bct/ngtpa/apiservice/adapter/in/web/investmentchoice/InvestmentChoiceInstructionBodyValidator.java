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

  private static final String SWITCH_SOURCES_MEMBER = "switchSources";
  private static final String ALLOCATIONS_MEMBER = "allocations";
  private static final String PERCENTAGE_MEMBER = "percentage";
  private static final String FUND_CODE_MEMBER = "fundCode";
  private static final String ALLOCATIONS_TARGET_PREFIX = "allocations[";
  private static final Set<String> BODY_MEMBERS = Set.of(SWITCH_SOURCES_MEMBER, ALLOCATIONS_MEMBER);
  private static final Set<String> ALLOCATION_MEMBERS = Set.of(FUND_CODE_MEMBER, PERCENTAGE_MEMBER);

  public ValidationResult validate(JsonNode body) {
    if (hasMalformedStructure(body)) {
      return invalid(
          List.of(
              new InvestmentChoiceViolation(
                  InvestmentChoiceViolation.Kind.FORM,
                  ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
                  List.of())));
    }

    JsonNode switchSources = body.get(SWITCH_SOURCES_MEMBER);
    JsonNode allocations = body.get(ALLOCATIONS_MEMBER);
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
    return hasMalformedArrayShapes(body)
        || hasMalformedSwitchSourceItems(body.get(SWITCH_SOURCES_MEMBER))
        || hasMalformedAllocationItems(body.get(ALLOCATIONS_MEMBER));
  }

  private boolean hasMalformedArrayShapes(JsonNode body) {
    JsonNode switchSources = body.get(SWITCH_SOURCES_MEMBER);
    if (switchSources != null && !switchSources.isArray()) {
      return true;
    }
    JsonNode allocations = body.get(ALLOCATIONS_MEMBER);
    return allocations != null && !allocations.isArray();
  }

  private boolean hasMalformedSwitchSourceItems(JsonNode switchSources) {
    if (switchSources == null) {
      return false;
    }
    for (JsonNode source : switchSources) {
      if (!source.isTextual()) {
        return true;
      }
    }
    return false;
  }

  private boolean hasMalformedAllocationItems(JsonNode allocations) {
    if (allocations == null) {
      return false;
    }
    for (JsonNode allocation : allocations) {
      if (hasMalformedAllocation(allocation)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasMalformedAllocation(JsonNode allocation) {
    if (!allocation.isObject() || containsUnknownMember(allocation, ALLOCATION_MEMBERS)) {
      return true;
    }
    JsonNode fundCode = allocation.get(FUND_CODE_MEMBER);
    return fundCode != null && !fundCode.isTextual();
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
                  SWITCH_SOURCES_MEMBER + "[" + index + "]"));
        }
      }
    }
    if (required) {
      violations.add(
          violation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
              SWITCH_SOURCES_MEMBER));
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
              ALLOCATIONS_MEMBER));
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
      JsonNode fundCodeNode = allocation.get(FUND_CODE_MEMBER);
      if (fundCodeNode == null || fundCodeNode.textValue().isBlank()) {
        missingFunds.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                ALLOCATIONS_TARGET_PREFIX + index + "]." + FUND_CODE_MEMBER));
      } else if (!seenFunds.add(fundCodeNode.textValue())) {
        duplicateFunds.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                ALLOCATIONS_TARGET_PREFIX + index + "]." + FUND_CODE_MEMBER));
      }

      JsonNode percentage = allocation.get(PERCENTAGE_MEMBER);
      if (!validPercentage(percentage)) {
        invalidPercentages.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                ALLOCATIONS_TARGET_PREFIX + index + "]." + PERCENTAGE_MEMBER));
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
              ALLOCATIONS_MEMBER));
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
              allocation.get(FUND_CODE_MEMBER).textValue(),
              allocation.get(PERCENTAGE_MEMBER).intValue()));
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
