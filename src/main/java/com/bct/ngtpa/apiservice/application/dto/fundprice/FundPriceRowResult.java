package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One assembled fund-price row for display, carrying BOTH bidPrice and offerPrice always --
 * fundPriceType-driven field selection (the bid/offer pair vs. a single unit price) is a WEB-layer
 * concern, resolved later by the web mapper, never here. riskLevel is deliberately
 * nullable/blank-legitimate.
 *
 * <p>chineseFundName is carried forward from {@link RawFundPriceRow} (which already has it) but
 * deliberately NOT validated here -- mirrors {@code RawFundPriceRow}'s own established precedent
 * of leaving this field unvalidated, since not every fund is guaranteed to have a populated
 * Chinese name from the downstream system. Language-based selection between fundName and
 * chineseFundName happens in the WEB layer (FundPriceWebMapper), never here.
 */
public record FundPriceRowResult(
    String fundCode,
    String fundName,
    String chineseFundName,
    LocalDate priceDate,
    String fundCurrency,
    BigDecimal bidPrice,
    BigDecimal offerPrice,
    String riskLevel) {

  public FundPriceRowResult {
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
