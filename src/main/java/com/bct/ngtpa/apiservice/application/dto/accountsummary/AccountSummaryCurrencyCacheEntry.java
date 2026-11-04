package com.bct.ngtpa.apiservice.application.dto.accountsummary;

import com.bct.ngtpa.apiservice.application.dto.CurrencySelection;
import java.util.List;
import java.util.Objects;

/**
 * The account-summary currency selection cached in the authenticated session, per
 * docs/refactor/3-account-endpoints-restructure/8-investgl-curr-required-addendum.md D-2.
 *
 * <p>Holds the available-currency list returned by TRPGetMemberAccBal on the most recent
 * blank-currency (first) load, bound to the account it was fetched for. An entry matches only
 * when both {@code accountRef} and {@code policyNo} equal the current account context, mirroring
 * {@code FundComparisonOptionsCacheEntry}'s account-identity rule. {@code toString()} hides both
 * identifiers.
 */
public record AccountSummaryCurrencyCacheEntry(
    String accountRef, String policyNo, List<String> availableCurrencies) {

  public AccountSummaryCurrencyCacheEntry {
    requireText(accountRef, "accountRef");
    requireText(policyNo, "policyNo");
    Objects.requireNonNull(availableCurrencies, "availableCurrencies must not be null");
    if (availableCurrencies.isEmpty()) {
      throw new IllegalArgumentException("availableCurrencies must not be empty");
    }
    for (String currency : availableCurrencies) {
      if (currency == null || currency.isBlank()) {
        throw new IllegalArgumentException("availableCurrencies must not contain a blank entry");
      }
    }
    availableCurrencies = List.copyOf(availableCurrencies);
  }

  /** Builds an entry from a freshly fetched balance currency selection. */
  public static AccountSummaryCurrencyCacheEntry of(
      String accountRef, String policyNo, CurrencySelection selection) {
    Objects.requireNonNull(selection, "selection must not be null");
    return new AccountSummaryCurrencyCacheEntry(
        accountRef, policyNo, selection.availableCurrencies());
  }

  /** True only when both identifiers match; anything else is a cache miss. */
  public boolean belongsTo(String accountRef, String policyNo) {
    return this.accountRef.equals(accountRef) && this.policyNo.equals(policyNo);
  }

  @Override
  public String toString() {
    return "AccountSummaryCurrencyCacheEntry[accountRef=<redacted>, policyNo=<redacted>, "
        + "availableCurrencies="
        + availableCurrencies
        + "]";
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
