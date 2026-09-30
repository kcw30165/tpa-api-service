package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.util.List;

/** Normalized, reconciled result of the TRPGetFundPrice operation. */
public record RawFundPriceData(List<RawFundPriceRow> rows) {

  public RawFundPriceData {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
