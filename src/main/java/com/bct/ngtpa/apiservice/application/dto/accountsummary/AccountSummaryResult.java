package com.bct.ngtpa.apiservice.application.dto.accountsummary;

import com.bct.ngtpa.apiservice.application.dto.AccountOverview;
import com.bct.ngtpa.apiservice.application.dto.CurrencySelection;
import com.bct.ngtpa.apiservice.application.dto.InvestmentPortfolio;

/**
 * Application-level summary result. currencySelection, accountOverview, and investmentPortfolio are
 * the SAME types GetAccountBalanceService already returns - reused by reference, not redeclared, so
 * the two features cannot drift apart. accountNumber and gainLossBreakdown are the only additions.
 */
public record AccountSummaryResult(
    String accountNumber,
    String asOfDate,
    CurrencySelection currencySelection,
    AccountOverview accountOverview,
    InvestmentPortfolio investmentPortfolio,
    GainLossBreakdown gainLossBreakdown) {}
