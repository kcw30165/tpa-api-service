package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The inclusive {@code fromDate}/{@code toDate} window computed by {@link
 * FundComparisonDateRangeCalculator}, per docs/design/13-fund-comparison/01-solution-design.md
 * section 1.4. Public/downstream date formatting (dd/MM/yyyy) is a separate, later concern
 * (adapter/mapper layer); this record carries plain {@link LocalDate} values only.
 */
public record FundComparisonDateRange(LocalDate fromDate, LocalDate toDate) {

  public FundComparisonDateRange {
    Objects.requireNonNull(fromDate, "fromDate must not be null");
    Objects.requireNonNull(toDate, "toDate must not be null");
    if (fromDate.isAfter(toDate)) {
      throw new IllegalArgumentException("fromDate must not be after toDate");
    }
  }
}
