package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.util.List;

/** Mirrors {@link FundPriceQueryResult}'s exact shape for the export path. */
public record FundPriceExportResult(
    List<FundPriceRowResult> rows, String fundPriceType, String maxRiskLevel) {

  public FundPriceExportResult {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
