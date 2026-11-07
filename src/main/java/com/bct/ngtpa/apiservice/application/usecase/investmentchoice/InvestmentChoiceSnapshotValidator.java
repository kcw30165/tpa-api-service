package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InvestmentChoiceSnapshotValidator {

  public List<InvestmentChoiceViolation> validate(
      InvestmentChoiceInstructionRequest request, InvestmentChoiceSnapshot snapshot) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");

    List<InvestmentChoiceViolation> violations = new ArrayList<>();
    for (int index = 0; index < request.allocations().size(); index++) {
      var allocation = request.allocations().get(index);
      if (allocation.percentage() % snapshot.intervalPcnt() != 0) {
        violations.add(
            violation(
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[" + index + "].percentage"));
      }
    }

    for (int index = 0; index < request.switchSources().size(); index++) {
      String requestedSource = request.switchSources().get(index);
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

    for (int index = 0; index < request.allocations().size(); index++) {
      String requestedFund = request.allocations().get(index).fundCode();
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
    return List.copyOf(violations);
  }

  private static InvestmentChoiceViolation violation(String code, String target) {
    return new InvestmentChoiceViolation(
        InvestmentChoiceViolation.Kind.FIELD, code, List.of(target));
  }
}
