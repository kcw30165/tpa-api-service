package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** maxRiskLevel is nullable and omitted from JSON when null, per the conditional-echo rule. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FundPriceQueryResponse(List<FundPriceRowResponse> rows, String maxRiskLevel) {

  public FundPriceQueryResponse {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
