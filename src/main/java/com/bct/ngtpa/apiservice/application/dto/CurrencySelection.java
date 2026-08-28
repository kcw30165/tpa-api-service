package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record CurrencySelection(String selectedCurrency, List<String> availableCurrencies) {
  public CurrencySelection {
    availableCurrencies = availableCurrencies == null ? List.of() : List.copyOf(availableCurrencies);
  }
}
