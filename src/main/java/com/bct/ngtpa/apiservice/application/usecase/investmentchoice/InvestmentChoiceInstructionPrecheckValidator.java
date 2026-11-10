package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionValidationResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.Allocation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonScalar;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class InvestmentChoiceInstructionPrecheckValidator {

  private static final String SWITCH_SOURCES = "switchSources";
  private static final String ALLOCATIONS = "allocations";
  private static final String FUND_CODE = "fundCode";
  private static final String PERCENTAGE = "percentage";
  private static final String ALLOCATIONS_TARGET_PREFIX = "allocations[";
  private static final Set<String> BODY_MEMBERS = Set.of(SWITCH_SOURCES, ALLOCATIONS);
  private static final Set<String> ALLOCATION_MEMBERS = Set.of(FUND_CODE, PERCENTAGE);

  public InvestmentChoiceInstructionValidationResult validate(
      ChangeInvestmentChoicePasswordValidationPayload payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    if (hasMalformedStructure(payload)) {
      return invalid(
          List.of(
              new InvestmentChoiceViolation(
                  InvestmentChoiceViolation.Kind.FORM,
                  ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
                  List.of())));
    }

    List<InvestmentChoiceViolation> violations = new ArrayList<>();
    appendSwitchSourceViolations(payload, violations);
    appendAllocationViolations(payload.allocationsKind(), payload.allocations(), violations);
    if (!violations.isEmpty()) {
      return invalid(violations);
    }
    return valid(toRequest(payload));
  }

  private boolean hasMalformedStructure(ChangeInvestmentChoicePasswordValidationPayload payload) {
    if (payload.rootKind() != JsonKind.OBJECT
        || containsUnknownMember(payload.rootMembers(), BODY_MEMBERS)) {
      return true;
    }
    if (payload.switchSourcesKind() != JsonKind.MISSING
        && payload.switchSourcesKind() != JsonKind.ARRAY) {
      return true;
    }
    if (payload.allocationsKind() != JsonKind.MISSING
        && payload.allocationsKind() != JsonKind.ARRAY) {
      return true;
    }
    return hasMalformedSwitchSources(payload.switchSources())
        || hasMalformedAllocations(payload.allocations());
  }

  private boolean hasMalformedSwitchSources(List<JsonScalar> switchSources) {
    return switchSources.stream().anyMatch(source -> source.kind() != JsonKind.STRING);
  }

  private boolean hasMalformedAllocations(List<Allocation> allocations) {
    for (Allocation allocation : allocations) {
      if (allocation.kind() != JsonKind.OBJECT
          || containsUnknownMember(allocation.memberNames(), ALLOCATION_MEMBERS)
          || (allocation.fundCode().kind() != JsonKind.MISSING
              && allocation.fundCode().kind() != JsonKind.STRING)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsUnknownMember(List<String> memberNames, Set<String> allowedMembers) {
    return memberNames.stream().anyMatch(member -> !allowedMembers.contains(member));
  }

  private void appendSwitchSourceViolations(
      ChangeInvestmentChoicePasswordValidationPayload payload,
      List<InvestmentChoiceViolation> violations) {
    boolean required =
        payload.switchSourcesKind() == JsonKind.MISSING || payload.switchSources().isEmpty();
    List<InvestmentChoiceViolation> duplicates = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int index = 0; index < payload.switchSources().size(); index++) {
      String source = payload.switchSources().get(index).scalarValue();
      if (source.isBlank()) {
        required = true;
      }
      if (!seen.add(source)) {
        duplicates.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                SWITCH_SOURCES + "[" + index + "]"));
      }
    }
    if (required) {
      violations.add(
          violation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
              SWITCH_SOURCES));
    }
    violations.addAll(duplicates);
  }

  private void appendAllocationViolations(
      JsonKind allocationsKind,
      List<Allocation> allocations,
      List<InvestmentChoiceViolation> violations) {
    if (allocationsKind == JsonKind.MISSING || allocations.isEmpty()) {
      violations.add(
          violation(
              InvestmentChoiceViolation.Kind.FIELD,
              ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
              ALLOCATIONS));
      return;
    }

    List<InvestmentChoiceViolation> missingFunds = new ArrayList<>();
    List<InvestmentChoiceViolation> duplicateFunds = new ArrayList<>();
    List<InvestmentChoiceViolation> invalidPercentages = new ArrayList<>();
    Set<String> seenFunds = new HashSet<>();
    long percentageTotal = 0;
    boolean allPercentagesValid = true;
    for (int index = 0; index < allocations.size(); index++) {
      Allocation allocation = allocations.get(index);
      JsonScalar fundCode = allocation.fundCode();
      if (fundCode.kind() == JsonKind.MISSING || fundCode.scalarValue().isBlank()) {
        missingFunds.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                ALLOCATIONS_TARGET_PREFIX + index + "]." + FUND_CODE));
      } else if (!seenFunds.add(fundCode.scalarValue())) {
        duplicateFunds.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                ALLOCATIONS_TARGET_PREFIX + index + "]." + FUND_CODE));
      }

      JsonScalar percentage = allocation.percentage();
      if (!validPercentage(percentage)) {
        invalidPercentages.add(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                ALLOCATIONS_TARGET_PREFIX + index + "]." + PERCENTAGE));
        allPercentagesValid = false;
      } else {
        percentageTotal += percentage.integerValue();
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
              ALLOCATIONS));
    }
  }

  private boolean validPercentage(JsonScalar percentage) {
    return percentage.kind() == JsonKind.NUMBER
        && percentage.integralNumber()
        && percentage.integerValue() != null
        && percentage.integerValue() >= 1
        && percentage.integerValue() <= 100;
  }

  private InvestmentChoiceInstructionRequest toRequest(
      ChangeInvestmentChoicePasswordValidationPayload payload) {
    List<String> switchSources =
        payload.switchSources().stream().map(JsonScalar::scalarValue).toList();
    List<InvestmentChoiceInstructionRequest.Allocation> allocations =
        payload.allocations().stream()
            .map(
                allocation ->
                    new InvestmentChoiceInstructionRequest.Allocation(
                        allocation.fundCode().scalarValue(),
                        allocation.percentage().integerValue()))
            .toList();
    return new InvestmentChoiceInstructionRequest(switchSources, allocations);
  }

  private static InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String target) {
    return new InvestmentChoiceViolation(kind, code, List.of(target));
  }

  private static InvestmentChoiceInstructionValidationResult valid(
      InvestmentChoiceInstructionRequest request) {
    return new InvestmentChoiceInstructionValidationResult(request, List.of());
  }

  private static InvestmentChoiceInstructionValidationResult invalid(
      List<InvestmentChoiceViolation> violations) {
    return new InvestmentChoiceInstructionValidationResult(null, violations);
  }
}
