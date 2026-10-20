package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.util.List;
import java.util.Objects;

/**
 * A prices selection confirmed against offered options: the fund codes and time-range token in the
 * spelling returned by TRPGetFundCmpOpt (docs/design/13-fund-comparison/09 section 9.5.4), used for
 * date calculation, the downstream fund-list, and the response series.
 */
public record FundComparisonSelection(List<String> fundCodes, String timeRange) {

  public FundComparisonSelection {
    Objects.requireNonNull(fundCodes, "fundCodes must not be null");
    if (fundCodes.isEmpty()) {
      throw new IllegalArgumentException("fundCodes must not be empty");
    }
    for (String fundCode : fundCodes) {
      if (fundCode == null || fundCode.isBlank()) {
        throw new IllegalArgumentException("fundCodes must not contain a blank entry");
      }
    }
    if (timeRange == null || timeRange.isBlank()) {
      throw new IllegalArgumentException("timeRange must not be blank");
    }
    fundCodes = List.copyOf(fundCodes);
  }
}
