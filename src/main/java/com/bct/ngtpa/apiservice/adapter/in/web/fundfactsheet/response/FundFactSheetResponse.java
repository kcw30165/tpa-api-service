package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response;

import java.util.List;

/** The data block of the fund fact sheet response. {@code rows} is always present. */
public record FundFactSheetResponse(List<FundFactSheetRowResponse> rows) {

  public FundFactSheetResponse {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
