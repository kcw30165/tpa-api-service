package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvestmentChoiceSnapshotTest {

  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";
  private static final String PENDING_REF_ONE = "260001449";
  private static final String PENDING_REF_TWO = "260001450";
  private static final String PENDING_REF_THREE = "260001451";

  @Test
  void mapsEverySourceAndFundFromDetailsIncludingUnavailableEntries() {
    var details =
        new InvestmentChoiceDetails.Normal(
            5,
            List.of(
                new InvestmentChoiceSource("BO", "Both source", 1, true),
                new InvestmentChoiceSource("EE", "Employee source", 2, false)),
            List.of(
                new InvestmentChoiceFund("FUND_A", "A", null, 60, null, true),
                new InvestmentChoiceFund("FUND_B", "B", null, 20, null, false)),
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
    assertThat(snapshot.pendingRefNos()).isEmpty();
  }

  @Test
  void fromDetailsCollectsDistinctPendingReferencesInFirstAppearanceOrder() {
    var details =
        new InvestmentChoiceDetails.Normal(
            5,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                pending(PENDING_REF_ONE),
                pending(PENDING_REF_TWO),
                pending(PENDING_REF_ONE),
                pending(PENDING_REF_THREE)));

    var snapshot = InvestmentChoiceSnapshot.fromDetails(ACCOUNT_REF, POLICY_NO, details);

    assertThat(snapshot.pendingRefNos())
        .containsExactly(PENDING_REF_ONE, PENDING_REF_TWO, PENDING_REF_THREE);
  }

  @Test
  void belongsToRequiresBothIdentifiers() {
    var snapshot =
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF,
            POLICY_NO,
            5,
            List.of(new InvestmentChoiceSnapshot.SwitchSource("BO", true)),
            List.of(new InvestmentChoiceSnapshot.Fund("FUND_A", true)),
            List.of());

    assertThat(snapshot.belongsTo(ACCOUNT_REF, POLICY_NO)).isTrue();
    assertThat(snapshot.belongsTo("acc_otherotherotherotherother", POLICY_NO)).isFalse();
    assertThat(snapshot.belongsTo(ACCOUNT_REF, "00000009999")).isFalse();
    assertThat(snapshot.belongsTo(null, POLICY_NO)).isFalse();
    assertThat(snapshot.belongsTo(ACCOUNT_REF, null)).isFalse();
  }

  @Test
  void rejectsInvalidIdentifiersAndNullLists() {
    List<InvestmentChoiceSnapshot.SwitchSource> emptySources = List.of();
    List<InvestmentChoiceSnapshot.Fund> emptyFunds = List.of();
    List<String> emptyPendingReferences = List.of();

    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    null, POLICY_NO, 5, emptySources, emptyFunds, emptyPendingReferences))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, " ", 5, emptySources, emptyFunds, emptyPendingReferences))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, POLICY_NO, 5, null, emptyFunds, emptyPendingReferences))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, POLICY_NO, 5, emptySources, null, emptyPendingReferences))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, POLICY_NO, 5, emptySources, emptyFunds, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullOrBlankPendingReferencesWithoutIncludingValuesInMessages() {
    List<InvestmentChoiceSnapshot.SwitchSource> emptySources = List.of();
    List<InvestmentChoiceSnapshot.Fund> emptyFunds = List.of();
    List<String> nullPendingReferences = Arrays.asList(PENDING_REF_ONE, null);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, POLICY_NO, 5, emptySources, emptyFunds, nullPendingReferences))
        .isInstanceOf(NullPointerException.class);
    List<String> blankPendingReferences = List.of(" ");
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF, POLICY_NO, 5, emptySources, emptyFunds, blankPendingReferences))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("pendingRefNos elements must not be blank");
  }

  @Test
  void acceptsEmptyAndMultiplePendingReferencesAndCopiesThemImmutably() {
    var empty = snapshotWithPendingReferences(List.of());
    List<String> mutableReferences = new ArrayList<>(List.of(PENDING_REF_ONE, PENDING_REF_TWO));
    var snapshot = snapshotWithPendingReferences(mutableReferences);
    mutableReferences.add(PENDING_REF_THREE);

    assertThat(empty.pendingRefNos()).isEmpty();
    assertThat(snapshot.pendingRefNos()).containsExactly(PENDING_REF_ONE, PENDING_REF_TWO);
    List<String> immutableReferences = snapshot.pendingRefNos();
    assertThatThrownBy(() -> immutableReferences.add(PENDING_REF_THREE))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void withoutPendingRefRemovesOnlyTheRequestedValueAndPreservesOrder() {
    var snapshot =
        snapshotWithPendingReferences(List.of(PENDING_REF_ONE, PENDING_REF_TWO, PENDING_REF_THREE));

    var updated = snapshot.withoutPendingRef(PENDING_REF_TWO);
    var unchanged = snapshot.withoutPendingRef("260001452");

    assertThat(updated.pendingRefNos()).containsExactly(PENDING_REF_ONE, PENDING_REF_THREE);
    assertThat(snapshot.pendingRefNos())
        .containsExactly(PENDING_REF_ONE, PENDING_REF_TWO, PENDING_REF_THREE);
    assertThat(unchanged.pendingRefNos())
        .containsExactly(PENDING_REF_ONE, PENDING_REF_TWO, PENDING_REF_THREE);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 101})
  void rejectsIntervalOutsideInclusiveRange(int intervalPcnt) {
    List<InvestmentChoiceSnapshot.SwitchSource> emptySources = List.of();
    List<InvestmentChoiceSnapshot.Fund> emptyFunds = List.of();
    List<String> emptyPendingReferences = List.of();

    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshot(
                    ACCOUNT_REF,
                    POLICY_NO,
                    intervalPcnt,
                    emptySources,
                    emptyFunds,
                    emptyPendingReferences))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("intervalPcnt must be between 1 and 100");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100})
  void acceptsInclusiveIntervalBoundaries(int intervalPcnt) {
    var snapshot =
        new InvestmentChoiceSnapshot(
            ACCOUNT_REF, POLICY_NO, intervalPcnt, List.of(), List.of(), List.of());

    assertThat(snapshot.intervalPcnt()).isEqualTo(intervalPcnt);
  }

  private static InvestmentChoiceSnapshot snapshotWithPendingReferences(List<String> references) {
    return new InvestmentChoiceSnapshot(
        ACCOUNT_REF, POLICY_NO, 5, List.of(), List.of(), references);
  }

  private static InvestmentChoicePending pending(String referenceNo) {
    return new InvestmentChoicePending(
        referenceNo, LocalDate.of(2026, 11, 6), LocalTime.of(9, 15), List.of());
  }
}
