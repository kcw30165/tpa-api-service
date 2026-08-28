package com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio;

import java.util.List;

public record RawFundLevelView(List<RawFundLevelDisplayAccount> displayAccounts) {
  public RawFundLevelView {
    displayAccounts = displayAccounts == null ? List.of() : List.copyOf(displayAccounts);
  }
}
