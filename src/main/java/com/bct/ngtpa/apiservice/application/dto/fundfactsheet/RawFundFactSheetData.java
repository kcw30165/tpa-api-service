package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import java.util.List;

/** Rows returned by the fund fact sheet source port, in downstream order. May be empty. */
public record RawFundFactSheetData(List<RawFundFactSheetRow> rows) {

  public RawFundFactSheetData {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
