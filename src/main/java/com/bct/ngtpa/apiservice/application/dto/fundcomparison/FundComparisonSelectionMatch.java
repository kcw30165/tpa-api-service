package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.util.List;
import java.util.Objects;

/**
 * Result of matching a prices request against offered options: either a {@link
 * FundComparisonSelection}, or a rejection reason. {@code unlistedFundCodes} lists requested codes
 * that were not offered (reported for logging; fund codes are not personal data).
 */
public record FundComparisonSelectionMatch(
    FundComparisonSelection selection, String reason, List<String> unlistedFundCodes) {

  public static final String TIME_RANGE_NOT_OFFERED = "time-range-not-offered";
  public static final String FUND_CODE_NOT_OFFERED = "fund-code-not-offered";
  public static final String NO_VALID_TIME_RANGE = "no-valid-time-range";

  public FundComparisonSelectionMatch {
    if ((selection == null) == (reason == null)) {
      throw new IllegalArgumentException("exactly one of selection or reason must be present");
    }
    unlistedFundCodes = unlistedFundCodes == null ? List.of() : List.copyOf(unlistedFundCodes);
  }

  public static FundComparisonSelectionMatch matched(FundComparisonSelection selection) {
    Objects.requireNonNull(selection, "selection must not be null");
    return new FundComparisonSelectionMatch(selection, null, List.of());
  }

  public static FundComparisonSelectionMatch rejected(
      String reason, List<String> unlistedFundCodes) {
    return new FundComparisonSelectionMatch(null, reason, unlistedFundCodes);
  }

  public boolean isMatched() {
    return selection != null;
  }
}
