package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import java.util.Objects;

/**
 * Wraps {@link FundPriceQueryResponse} under a "data" field so that {@code
 * MutationResponse<FundPriceQueryResultResponse>} serializes as {@code {success, status, result:
 * {data: {rows, maxRiskLevel}}, messages, errors}}, matching {@link FundPriceModeResultResponse}.
 */
public record FundPriceQueryResultResponse(FundPriceQueryResponse data) {

  public FundPriceQueryResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
