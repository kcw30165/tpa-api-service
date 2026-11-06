package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

/** Submit-time investment choice constraints bound to the account that supplied them. */
public record InvestmentChoiceSnapshot(
    String accountRef,
    String policyNo,
    int intervalPcnt,
    List<SwitchSource> sources,
    List<Fund> funds) {

  public InvestmentChoiceSnapshot {
    requireText(accountRef, "accountRef");
    requireText(policyNo, "policyNo");
    sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
    funds = List.copyOf(Objects.requireNonNull(funds, "funds must not be null"));
  }

  public static InvestmentChoiceSnapshot fromDetails(
      String accountRef, String policyNo, InvestmentChoiceDetails details) {
    Objects.requireNonNull(details, "details must not be null");
    return new InvestmentChoiceSnapshot(
        accountRef,
        policyNo,
        details.intervalPcnt(),
        details.sources().stream()
            .map(source -> new SwitchSource(source.code(), source.avail()))
            .toList(),
        details.funds().stream().map(fund -> new Fund(fund.code(), fund.avail())).toList());
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
