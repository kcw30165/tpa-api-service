package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One normalized, reconciled fund-price row (a FundPrice entry joined with its matching FundInfo
 * entry by fund-code).
 *
 * <p>Carries BOTH {@code bidPrice} and {@code offerPrice} always -- fundPriceType-driven field
 * selection (the bid/offer pair vs. a single unit price) is a display concern resolved later in
 * the web mapper, never here. {@code riskLevel} is deliberately nullable/blank-legitimate, matching
 * every other risk-level field in this design pack.
 */
public record RawFundPriceRow(
    String fundCode,
    String fundName,
    String chineseFundName,
    LocalDate priceDate,
    String fundCurrency,
    BigDecimal bidPrice,
    BigDecimal offerPrice,
    String riskLevel) {

  public RawFundPriceRow {
    if (fundCode == null || fundCode.isBlank()) {
      throw new IllegalArgumentException("fundCode must not be blank");
    }
    if (fundName == null || fundName.isBlank()) {
      throw new IllegalArgumentException("fundName must not be blank");
    }
    Objects.requireNonNull(priceDate, "priceDate must not be null");
    if (fundCurrency == null || fundCurrency.isBlank()) {
      throw new IllegalArgumentException("fundCurrency must not be blank");
    }
    Objects.requireNonNull(bidPrice, "bidPrice must not be null");
    Objects.requireNonNull(offerPrice, "offerPrice must not be null");
  }
}
