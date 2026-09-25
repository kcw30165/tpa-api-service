package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.util.List;

/**
 * fundPriceType drives the web mapper's later bid/offer-vs-unit branching. maxRiskLevel is
 * nullable, sourced from cached session metadata, and is NOT re-validated here (already validated,
 * or deliberately not validated, upstream).
 */
public record FundPriceQueryResult(
    List<FundPriceRowResult> rows, String fundPriceType, String maxRiskLevel) {

  public FundPriceQueryResult {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
