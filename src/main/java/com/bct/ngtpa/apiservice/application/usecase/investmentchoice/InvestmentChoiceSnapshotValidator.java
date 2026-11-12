package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.Allocation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonScalar;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InvestmentChoiceSnapshotValidator {

  public List<InvestmentChoiceViolation> validate(
      ChangeInvestmentChoicePasswordValidationPayload payload, InvestmentChoiceSnapshot snapshot) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");

    List<InvestmentChoiceViolation> violations = new ArrayList<>();
    appendPercentageViolations(payload, snapshot, violations);
    appendSourceViolations(payload, snapshot, violations);
    appendFundViolations(payload, snapshot, violations);
    return List.copyOf(violations);
  }

  private static void appendPercentageViolations(
      ChangeInvestmentChoicePasswordValidationPayload payload,
      InvestmentChoiceSnapshot snapshot,
      List<InvestmentChoiceViolation> violations) {
    for (int index = 0; index < payload.allocations().size(); index++) {
      Allocation allocation = payload.allocations().get(index);
      if (allocation.kind() != JsonKind.OBJECT) {
        continue;
      }
      JsonScalar percentage = allocation.percentage();
      if (InvestmentChoiceInstructionPrecheckValidator.validPercentage(percentage)
          && percentage.integerValue() % snapshot.intervalPcnt() != 0) {
        violations.add(
            violation(
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[" + index + "].percentage"));
      }
    }
  }

  private static void appendSourceViolations(
      ChangeInvestmentChoicePasswordValidationPayload payload,
      InvestmentChoiceSnapshot snapshot,
      List<InvestmentChoiceViolation> violations) {
    for (int index = 0; index < payload.switchSources().size(); index++) {
      JsonScalar sourceValue = payload.switchSources().get(index);
      if (sourceValue.kind() != JsonKind.STRING
          || sourceValue.scalarValue() == null
          || sourceValue.scalarValue().isBlank()) {
        continue;
      }
      String requestedSource = sourceValue.scalarValue();
      boolean available =
          snapshot.sources().stream()
              .anyMatch(
                  source ->
                      Objects.equals(source.switchSource(), requestedSource) && source.available());
      if (!available) {
        violations.add(
            violation(
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[" + index + "]"));
      }
    }
  }

  private static void appendFundViolations(
      ChangeInvestmentChoicePasswordValidationPayload payload,
      InvestmentChoiceSnapshot snapshot,
      List<InvestmentChoiceViolation> violations) {
    for (int index = 0; index < payload.allocations().size(); index++) {
      Allocation allocation = payload.allocations().get(index);
      JsonScalar fundCode = allocation.fundCode();
      if (allocation.kind() != JsonKind.OBJECT
          || fundCode.kind() != JsonKind.STRING
          || fundCode.scalarValue() == null
          || fundCode.scalarValue().isBlank()) {
        continue;
      }
      String requestedFund = fundCode.scalarValue();
      boolean available =
          snapshot.funds().stream()
              .anyMatch(fund -> Objects.equals(fund.fundCode(), requestedFund) && fund.available());
      if (!available) {
        violations.add(
            violation(
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[" + index + "].fundCode"));
      }
    }
  }

  private static InvestmentChoiceViolation violation(String code, String target) {
    return new InvestmentChoiceViolation(
        InvestmentChoiceViolation.Kind.FIELD, code, List.of(target));
  }
}
