package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response;

import java.util.Objects;

/**
 * Wraps {@link FundFactSheetResponse} under a {@code data} field, so that the public body is
 * {@code {success, status, result: {data: {rows}}, messages, errors}}.
 */
public record FundFactSheetResultResponse(FundFactSheetResponse data) {

  public FundFactSheetResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
