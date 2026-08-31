package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.CurrencySelection;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceCurrency;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.util.LinkedHashSet;

public final class AccountBalanceCurrencySelectionMapper {
  public CurrencySelection map(RawAccountBalanceCurrency raw) {
    try {
      if (raw == null || raw.selected() == null || raw.selected().isBlank()) fail();
      var unique = new LinkedHashSet<String>();
      for (String value : raw.available()) {
        if (value == null || value.isBlank() || !unique.add(value)) fail();
      }
      if (!unique.contains(raw.selected())) fail();
      return new CurrencySelection(raw.selected(), java.util.List.copyOf(unique));
    } catch (AccountBalanceRetrievalException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw AccountBalanceRetrievalException.systemFailure();
    }
  }

  private static void fail() {
    throw AccountBalanceRetrievalException.systemFailure();
  }
}
