package com.bct.ngtpa.apiservice.application.dto.accountbalance.overview;
import java.util.List;
public record RawAccountOverviewAggregation(boolean vestedBalanceVisible, boolean pendingSwitchInVisible, List<RawDisplayAccountOverview> displayAccounts, RawAccountTotals accountTotals) {
  public RawAccountOverviewAggregation { displayAccounts = List.copyOf(displayAccounts); }
}
