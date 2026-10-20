package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.util.List;
import java.util.Objects;

/**
 * Normalized, validated output of {@link FundComparisonTimeRangeParser}: the ordered, deduplicated
 * list of valid time-range tokens (preserving normalized downstream order), and the selected
 * default token (fixed unit priority D &lt; M &lt; Y &lt; YTD; smallest integer within a unit;
 * independent of array order), per
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.2.
 */
public record FundComparisonTimeRangeParseResult(List<String> timeRanges, String defaultTimeRange) {

  public FundComparisonTimeRangeParseResult {
    Objects.requireNonNull(timeRanges, "timeRanges must not be null");
    if (timeRanges.isEmpty()) {
      throw new IllegalArgumentException("timeRanges must not be empty");
    }
    Objects.requireNonNull(defaultTimeRange, "defaultTimeRange must not be null");
    timeRanges = List.copyOf(timeRanges);
  }
}
