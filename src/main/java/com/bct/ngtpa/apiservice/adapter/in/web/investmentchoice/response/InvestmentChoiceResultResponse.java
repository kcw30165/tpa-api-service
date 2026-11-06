package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import java.util.Objects;

public record InvestmentChoiceResultResponse(InvestmentChoiceDataResponse data) {

  public InvestmentChoiceResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
