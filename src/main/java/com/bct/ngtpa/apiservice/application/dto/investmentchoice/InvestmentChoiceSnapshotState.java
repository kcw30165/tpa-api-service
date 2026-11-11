package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.Objects;
import java.util.Optional;

/** Account-owned CIM authorization state captured by GET. */
public record InvestmentChoiceSnapshotState(
    InvestmentChoiceScenario scenario,
    String accountRef,
    String policyNo,
    Optional<InvestmentChoiceSnapshot> normalSnapshot) {

  public InvestmentChoiceSnapshotState {
    Objects.requireNonNull(scenario, "scenario must not be null");
    requireText(accountRef, "accountRef");
    requireText(policyNo, "policyNo");
    normalSnapshot = Objects.requireNonNull(normalSnapshot, "normalSnapshot must not be null");
    if (scenario == InvestmentChoiceScenario.NORMAL) {
      if (normalSnapshot.isEmpty() || !normalSnapshot.get().belongsTo(accountRef, policyNo)) {
        throw new IllegalArgumentException("NORMAL state requires its matching snapshot");
      }
    } else if (normalSnapshot.isPresent()) {
      throw new IllegalArgumentException("Restricted state cannot contain a normal snapshot");
    }
  }

  public static InvestmentChoiceSnapshotState normal(InvestmentChoiceSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    return new InvestmentChoiceSnapshotState(
        InvestmentChoiceScenario.NORMAL,
        snapshot.accountRef(),
        snapshot.policyNo(),
        Optional.of(snapshot));
  }

  public static InvestmentChoiceSnapshotState restricted(
      String accountRef, String policyNo, InvestmentChoiceScenario scenario) {
    return new InvestmentChoiceSnapshotState(scenario, accountRef, policyNo, Optional.empty());
  }

  public static InvestmentChoiceSnapshotState fromDetails(
      String accountRef, String policyNo, InvestmentChoiceDetails details) {
    Objects.requireNonNull(details, "details must not be null");
    if (details instanceof InvestmentChoiceDetails.Normal normal) {
      return normal(InvestmentChoiceSnapshot.fromDetails(accountRef, policyNo, normal));
    }
    return restricted(accountRef, policyNo, details.scenario());
  }

  public boolean belongsTo(String accountRef, String policyNo) {
    return this.accountRef.equals(accountRef) && this.policyNo.equals(policyNo);
  }

  public InvestmentChoiceSnapshotState withoutPendingRef(String referenceNo) {
    InvestmentChoiceSnapshot snapshot =
        normalSnapshot.orElseThrow(
            () -> new IllegalStateException("Restricted state has no pending references"));
    return normal(snapshot.withoutPendingRef(referenceNo));
  }

  @Override
  public String toString() {
    return "InvestmentChoiceSnapshotState[scenario="
        + scenario
        + ", accountRef=<redacted>, policyNo=<redacted>]";
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
