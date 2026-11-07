package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvestmentChoiceSnapshotTest {

  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";

  @Test
  void mapsEverySourceAndFundFromDetailsIncludingUnavailableEntries() {
    var details =
        new InvestmentChoiceDetails(
            5,
            List.of(
                new InvestmentChoiceSource("BO", 1, true),
                new InvestmentChoiceSource("EE", 2, false)),
            List.of(
                new InvestmentChoiceFund("FUND_A", "A", null, null, true),
                new InvestmentChoiceFund("FUND_B", "B", null, null, false)),
            List.of(),
            List.of());

    var snapshot = InvestmentChoiceSnapshot.fromDetails(ACCOUNT_REF, POLICY_NO, details);

    assertThat(snapshot.accountRef()).isEqualTo(ACCOUNT_REF);
    assertThat(snapshot.policyNo()).isEqualTo(POLICY_NO);
    assertThat(snapshot.intervalPcnt()).isEqualTo(5);
    assertThat(snapshot.sources())
        .containsExactly(
            new InvestmentChoiceSnapshot.SwitchSource("BO", true),
            new InvestmentChoiceSnapshot.SwitchSource("EE", false));
    assertThat(snapshot.funds())
        .containsExactly(
            new InvestmentChoiceSnapshot.Fund("FUND_A", true),
            new InvestmentChoiceSnapshot.Fund("FUND_B", false));
  }

  @Test
  void belongsToRequiresBothIdentifiers() {
    var snapshot =
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF,
            POLICY_NO,
            5,
            List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", true)),
            List.of(new InvestmentChoiceSnapshot.Fund("FUND_A", true)));

    assertThat(snapshot.belongsTo(ACCOUNT_REF, POLICY_NO)).isTrue();
    assertThat(snapshot.belongsTo("acc_otherotherotherotherother", POLICY_NO)).isFalse();
    assertThat(snapshot.belongsTo(ACCOUNT_REF, "00000009999")).isFalse();
    assertThat(snapshot.belongsTo(null, POLICY_NO)).isFalse();
    assertThat(snapshot.belongsTo(ACCOUNT_REF, null)).isFalse();
  }

  @Test
  void rejectsInvalidIdentifiersAndNullLists() {
    assertThatThrownBy(() -> new InvestmentChoiceSnapshot(null, POLICY_NO, 5, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new InvestmentChoiceSnapshot(ACCOUNT_REF, " ", 5, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new InvestmentChoiceSnapshot(ACCOUNT_REF, POLICY_NO, 5, null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new InvestmentChoiceSnapshot(ACCOUNT_REF, POLICY_NO, 5, List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 101})
  void rejectsIntervalOutsideInclusiveRange(int intervalPcnt) {
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, POLICY_NO, intervalPcnt, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("intervalPcnt must be between 1 and 100");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100})
  void acceptsInclusiveIntervalBoundaries(int intervalPcnt) {
    var snapshot =
        new InvestmentChoiceSnapshot(ACCOUNT_REF, POLICY_NO, intervalPcnt, List.of(), List.of());

    assertThat(snapshot.intervalPcnt()).isEqualTo(intervalPcnt);
  }
}
