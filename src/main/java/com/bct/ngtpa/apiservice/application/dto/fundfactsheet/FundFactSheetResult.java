package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import java.util.List;

/** Normalized fund fact sheet rows in downstream order. An empty list means "no funds". */
public record FundFactSheetResult(List<FundFactSheetRowResult> rows) {

  public FundFactSheetResult {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
