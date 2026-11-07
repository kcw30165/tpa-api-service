package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentChoiceSnapshotValidatorTest {

  private final InvestmentChoiceSnapshotValidator validator =
      new InvestmentChoiceSnapshotValidator();

  private static InvestmentChoiceInstructionRequest request(
      List<String> switchSources, InvestmentChoiceInstructionRequest.Allocation... allocations) {
    return new InvestmentChoiceInstructionRequest(switchSources, List.of(allocations));
  }

  private static InvestmentChoiceSnapshot snapshot(
      int intervalPcnt,
      List<InvestmentChoiceSnapshot.SwitchSource> sources,
      List<InvestmentChoiceSnapshot.Fund> funds) {
    return new InvestmentChoiceSnapshot(
        "acc_validvaluevalidvaluevalidvalue", "policy-valid", intervalPcnt, sources, funds);
  }

  private static InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String target) {
    return new InvestmentChoiceViolation(kind, code, List.of(target));
  }

  @Test
  void reportsEveryAllocationThatIsNotAnIntervalMultiple() {
    var result =
        validator.validate(
            request(
                List.of("BO"),
                new InvestmentChoiceInstructionRequest.Allocation("DBLF", 60),
                new InvestmentChoiceInstructionRequest.Allocation("DHKM", 40)),
            snapshot(
                25,
                List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", true)),
                List.of(
                    new InvestmentChoiceSnapshot.Fund("DBLF", true),
                    new InvestmentChoiceSnapshot.Fund("DHKM", true))));

    assertThat(result)
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[0].percentage"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[1].percentage"));
  }

  @Test
  void sourceAvailabilityTrueIsEditableAndFalseIsReadOnly() {
    var snapshot =
        snapshot(
            5,
            List.of(
                new InvestmentChoiceSnapshot.SwitchSource("BO", true),
                new InvestmentChoiceSnapshot.SwitchSource("EE", false)),
            List.of(new InvestmentChoiceSnapshot.Fund("DBLF", true)));

    assertThat(
            validator.validate(
                request(
                    List.of("BO"), new InvestmentChoiceInstructionRequest.Allocation("DBLF", 100)),
                snapshot))
        .isEmpty();
    assertThat(
            validator.validate(
                request(
                    List.of("EE"), new InvestmentChoiceInstructionRequest.Allocation("DBLF", 100)),
                snapshot))
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[0]"));
  }

  @Test
  void reportsAbsentAndUnavailableFundsAtEachAllocationIndex() {
    var result =
        validator.validate(
            request(
                List.of("BO"),
                new InvestmentChoiceInstructionRequest.Allocation("DBLF", 50),
                new InvestmentChoiceInstructionRequest.Allocation("DHKM", 50)),
            snapshot(
                25,
                List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", true)),
                List.of(
                    new InvestmentChoiceSnapshot.Fund("DBLF", false),
                    new InvestmentChoiceSnapshot.Fund("FUND_C", true))));

    assertThat(result)
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[0].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[1].fundCode"));
  }

  @Test
  void reportsAllSnapshotViolationsInRuleAndIndexOrder() {
    var result =
        validator.validate(
            request(
                List.of("EE", "MISSING"),
                new InvestmentChoiceInstructionRequest.Allocation("DBLF", 60),
                new InvestmentChoiceInstructionRequest.Allocation("DHKM", 40)),
            snapshot(
                25,
                List.of(new InvestmentChoiceSnapshot.SwitchSource("EE", false)),
                List.of(new InvestmentChoiceSnapshot.Fund("DBLF", false))));

    assertThat(result)
        .containsExactly(
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[0].percentage"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                "allocations[1].percentage"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[0]"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                "switchSources[1]"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[0].fundCode"),
            violation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                "allocations[1].fundCode"));
  }
}
