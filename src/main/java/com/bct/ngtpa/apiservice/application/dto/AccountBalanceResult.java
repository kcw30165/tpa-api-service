package com.bct.ngtpa.apiservice.application.dto;

public record AccountBalanceResult(
    String asOfDate,
    CurrencySelection currencySelection,
    AccountOverview accountOverview,
    InvestmentPortfolio investmentPortfolio) {}
