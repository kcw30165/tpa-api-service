package com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance;

import java.util.List;

public record CurrencySelectionResponse(String selectedCurrency, List<String> availableCurrencies) {}
