package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import java.util.Objects;

public record InvestmentChoiceSubmissionResultResponse(
    InvestmentChoiceSubmissionDataResponse data) {

  public InvestmentChoiceSubmissionResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
