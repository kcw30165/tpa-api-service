package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

/** Submit-time investment choice constraints bound to the account that supplied them. */
public record InvestmentChoiceSnapshot(
    String accountRef,
    String policyNo,
    int intervalPcnt,
    List<SwitchSource> sources,
    List<Fund> funds,
    List<String> pendingRefNos) {

  public InvestmentChoiceSnapshot {
    requireText(accountRef, "accountRef");
    requireText(policyNo, "policyNo");
    if (intervalPcnt < 1 || intervalPcnt > 100) {
      throw new IllegalArgumentException("intervalPcnt must be between 1 and 100");
    }
    sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
    funds = List.copyOf(Objects.requireNonNull(funds, "funds must not be null"));
    pendingRefNos =
        List.copyOf(Objects.requireNonNull(pendingRefNos, "pendingRefNos must not be null"));
    if (pendingRefNos.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("pendingRefNos elements must not be blank");
    }
  }

  public static InvestmentChoiceSnapshot fromDetails(
      String accountRef, String policyNo, InvestmentChoiceDetails.Normal details) {
    Objects.requireNonNull(details, "details must not be null");
    return new InvestmentChoiceSnapshot(
        accountRef,
        policyNo,
        details.intervalPcnt(),
        details.sources().stream()
            .map(source -> new SwitchSource(source.code(), source.avail()))
            .toList(),
        details.funds().stream().map(fund -> new Fund(fund.code(), fund.avail())).toList(),
        details.pending().stream().map(InvestmentChoicePending::refNo).distinct().toList());
  }

  public InvestmentChoiceSnapshot withoutPendingRef(String referenceNo) {
    return new InvestmentChoiceSnapshot(
        accountRef,
        policyNo,
        intervalPcnt,
        sources,
        funds,
        pendingRefNos.stream().filter(reference -> !reference.equals(referenceNo)).toList());
  }

  /** True only when both identifiers match; anything else is a cache miss. */
  public boolean belongsTo(String accountRef, String policyNo) {
    return this.accountRef.equals(accountRef) && this.policyNo.equals(policyNo);
  }

  @Override
  public String toString() {
    return "InvestmentChoiceSnapshot[accountRef=<redacted>, policyNo=<redacted>, intervalPcnt="
        + intervalPcnt
        + ", sourceCount="
        + sources.size()
        + ", fundCount="
        + funds.size()
        + ", pendingRefCount="
        + pendingRefNos.size()
        + "]";
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  public record SwitchSource(String switchSource, boolean available) {}

  public record Fund(String fundCode, boolean available) {}
}
