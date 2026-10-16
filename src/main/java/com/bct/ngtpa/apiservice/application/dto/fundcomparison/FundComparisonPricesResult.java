package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Output of the Milestone 3 prices use case (sub-task 3.3): the resolved inclusive date window (per
 * 01-solution-design.md section 1.4), the validated/deduplicated requested fund codes in
 * first-occurrence order (needed by {@code FundComparisonPricesWebMapper#toResponse} to order and
 * select series entries), and the raw reconciled downstream data. Public response shaping (date
 * formatting, bid-price-to-unitPrice mapping, grouping) is the web mapper's responsibility, never
 * this record's.
 */
public record FundComparisonPricesResult(
    LocalDate fromDate, LocalDate toDate, List<String> fundCodes, RawFundPriceData rawData) {

  public FundComparisonPricesResult {
    Objects.requireNonNull(fromDate, "fromDate must not be null");
    Objects.requireNonNull(toDate, "toDate must not be null");
    if (fromDate.isAfter(toDate)) {
      throw new IllegalArgumentException("fromDate must not be after toDate");
    }
    Objects.requireNonNull(fundCodes, "fundCodes must not be null");
    fundCodes = List.copyOf(fundCodes);
    Objects.requireNonNull(rawData, "rawData must not be null");
  }
}
