package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.Objects;

public record InvestmentChoiceCancellation(String referenceNo) {

  public InvestmentChoiceCancellation {
    Objects.requireNonNull(referenceNo, "referenceNo must not be null");
  }
}
