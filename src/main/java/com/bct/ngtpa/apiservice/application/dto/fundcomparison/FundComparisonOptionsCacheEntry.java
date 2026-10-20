package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.util.List;
import java.util.Objects;

/**
 * The Fund Comparison options cached in the authenticated session, per
 * docs/design/13-fund-comparison/09-options-session-cache-change.md section 9.5.2.
 *
 * <p>Holds only the raw downstream {@code time-range} string and the downstream fund codes, exactly
 * as returned by TRPGetFundCmpOpt (no fund names, no default time range), bound to the account it
 * was fetched for. An entry matches only when both {@code accountRef} and {@code policyNo} equal
 * the current account context (D2). {@code toString()} hides both identifiers.
 */
public record FundComparisonOptionsCacheEntry(
    String accountRef, String policyNo, String rawTimeRange, List<String> fundCodes) {

  public FundComparisonOptionsCacheEntry {
    accountRef = requireText(accountRef, "accountRef");
    policyNo = requireText(policyNo, "policyNo");
    rawTimeRange = requireText(rawTimeRange, "rawTimeRange");
    Objects.requireNonNull(fundCodes, "fundCodes must not be null");
    if (fundCodes.isEmpty()) {
      throw new IllegalArgumentException("fundCodes must not be empty");
    }
    for (String fundCode : fundCodes) {
      if (fundCode == null || fundCode.isBlank()) {
        throw new IllegalArgumentException("fundCodes must not contain a blank entry");
      }
    }
    fundCodes = List.copyOf(fundCodes);
  }

  /** Builds an entry from freshly fetched options, keeping downstream fund-code order. */
  public static FundComparisonOptionsCacheEntry of(
      String accountRef, String policyNo, RawFundComparisonOptionsData raw) {
    Objects.requireNonNull(raw, "raw must not be null");
    return new FundComparisonOptionsCacheEntry(
        accountRef,
        policyNo,
        raw.rawTimeRange(),
        raw.funds().stream()
            .map(RawFundComparisonOptionsData.RawFundComparisonOption::fundCode)
            .toList());
  }

  /** True only when both identifiers match (D2); anything else is a cache miss. */
  public boolean belongsTo(String accountRef, String policyNo) {
    return this.accountRef.equals(accountRef) && this.policyNo.equals(policyNo);
  }

  @Override
  public String toString() {
    return "FundComparisonOptionsCacheEntry[accountRef=<redacted>, policyNo=<redacted>, rawTimeRange="
        + rawTimeRange
        + ", fundCodes="
        + fundCodes
        + "]";
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
