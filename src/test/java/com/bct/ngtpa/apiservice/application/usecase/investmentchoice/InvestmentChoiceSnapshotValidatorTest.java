package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.Allocation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.ChangeInvestmentChoicePasswordValidationPayload.JsonScalar;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentChoiceSnapshotValidatorTest {

  private final InvestmentChoiceSnapshotValidator validator =
      new InvestmentChoiceSnapshotValidator();

  private static ChangeInvestmentChoicePasswordValidationPayload payload(
      List<String> switchSources, Allocation... allocations) {
    return new ChangeInvestmentChoicePasswordValidationPayload(
        JsonKind.OBJECT,
        List.of("switchSources", "allocations"),
        JsonKind.ARRAY,
        switchSources.stream()
            .map(source -> new JsonScalar(JsonKind.STRING, source, false, null))
            .toList(),
        JsonKind.ARRAY,
        List.of(allocations));
  }

  private static Allocation allocation(String fundCode, int percentage) {
    return allocation(
        fundCode, new JsonScalar(JsonKind.NUMBER, Integer.toString(percentage), true, percentage));
  }

  private static Allocation allocation(String fundCode, JsonScalar percentage) {
    return new Allocation(
        JsonKind.OBJECT,
        List.of("fundCode", "percentage"),
        new JsonScalar(JsonKind.STRING, fundCode, false, null),
        percentage);
  }

  private static InvestmentChoiceSnapshot snapshot(
      int intervalPcnt,
      List<InvestmentChoiceSnapshot.SwitchSource> sources,
      List<InvestmentChoiceSnapshot.Fund> funds) {
    return new InvestmentChoiceSnapshot(
        "acc_validvaluevalidvaluevalidvalue",
        "policy-valid",
        intervalPcnt,
        sources,
        funds,
        List.of());
  }

  private static InvestmentChoiceViolation violation(
      InvestmentChoiceViolation.Kind kind, String code, String target) {
    return new InvestmentChoiceViolation(kind, code, List.of(target));
  }

  @Test
  void reportsEveryAllocationThatIsNotAnIntervalMultiple() {
    var result =
        validator.validate(
            payload(List.of("BO"), allocation("DBLF", 60), allocation("DHKM", 40)),
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

    assertThat(validator.validate(payload(List.of("BO"), allocation("DBLF", 100)), snapshot))
        .isEmpty();
    assertThat(validator.validate(payload(List.of("EE"), allocation("DBLF", 100)), snapshot))
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
            payload(List.of("BO"), allocation("DBLF", 50), allocation("DHKM", 50)),
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
            payload(List.of("EE", "MISSING"), allocation("DBLF", 60), allocation("DHKM", 40)),
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

  @Test
  void skipsIntervalCheckForUnreadablePercentageAndPreservesOriginalIndex() {
    var result =
        validator.validate(
            payload(
                List.of("BO"),
                allocation("DBLF", new JsonScalar(JsonKind.STRING, "unreadable", false, null)),
                allocation("DHKM", 40)),
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
                "allocations[1].percentage"));
  }
}
