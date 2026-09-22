package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.AccountOverviewResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.CurrencySelectionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.InvestmentPortfolioResponse;

/**
 * Account summary payload: the full Account Balance shape plus two additions.
 *
 * <p>The three Account Balance sections are reused by reference rather than redeclared, so the two
 * endpoints cannot drift apart. Their field names, nullability, and Jackson inclusion behaviour are
 * inherited unchanged, and {@code /api/v1/account/balance} consumers see no difference.
 *
 * <p>{@code accountNumber} is composed in the BFF from the selected account's protected context as
 * {@code policyNo + "-" + certNo}. Both components are already left-padded to their configured
 * widths by the login flow, giving a fixed 16-character value such as {@code 00000008802-0230}.
 *
 * <p>{@code gainLossBreakdown} is a sibling of {@code investmentPortfolio} and is present only on
 * the summary endpoint.
 */
public record AccountSummaryDataResponse(
    String accountNumber,
    String asOfDate,
    CurrencySelectionResponse currencySelection,
    AccountOverviewResponse accountOverview,
    InvestmentPortfolioResponse investmentPortfolio,
    GainLossBreakdownResponse gainLossBreakdown) {}
