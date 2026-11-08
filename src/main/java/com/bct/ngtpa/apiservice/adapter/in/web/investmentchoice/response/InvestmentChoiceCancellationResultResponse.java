package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import java.util.Objects;

public record InvestmentChoiceCancellationResultResponse(
    InvestmentChoiceCancellationDataResponse data) {

  public InvestmentChoiceCancellationResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
