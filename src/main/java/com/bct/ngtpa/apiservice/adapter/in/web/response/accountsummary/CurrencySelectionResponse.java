package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import java.util.List;

public record CurrencySelectionResponse(
    String selectedCurrency, List<String> availableCurrencies) {}
