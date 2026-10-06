package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import java.util.Objects;

/**
 * Wraps {@link FundPriceModeResponse} under a "data" field, mirroring the established {@code
 * AccountSummaryResultResponse}/{@code AccountGainLossResultResponse} convention so that {@code
 * MutationResponse<FundPriceModeResultResponse>}'s serialized "result" field itself carries a
 * nested "data" key, per Nick's confirmed target response shape: {@code {success, status, result:
 * {data: {...}}, messages, errors}}.
 */
public record FundPriceModeResultResponse(FundPriceModeResponse data) {

  public FundPriceModeResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
