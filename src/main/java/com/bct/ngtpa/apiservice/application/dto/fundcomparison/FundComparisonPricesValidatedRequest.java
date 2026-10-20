package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.util.List;
import java.util.Objects;

/**
 * Validated, deduplicated hand-off from {@code FundComparisonPricesWebMapper#validate} (Milestone
 * 3, sub-task 3.2) to the prices use case (sub-task 3.3), per
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.3: 1-5 unique
 * nonblank fund codes (deduplicated preserving first-occurrence order by the mapper before this
 * record is constructed) and a single syntactically valid time-range token.
 */
public record FundComparisonPricesValidatedRequest(List<String> fundCodes, String timeRange) {

  public FundComparisonPricesValidatedRequest {
    Objects.requireNonNull(fundCodes, "fundCodes must not be null");
    if (fundCodes.isEmpty()) {
      throw new IllegalArgumentException("fundCodes must not be empty");
    }
    if (fundCodes.size() > 5) {
      throw new IllegalArgumentException("fundCodes must not contain more than 5 entries");
    }
    if (timeRange == null || timeRange.isBlank()) {
      throw new IllegalArgumentException("timeRange must not be blank");
    }
    fundCodes = List.copyOf(fundCodes);
  }
}
