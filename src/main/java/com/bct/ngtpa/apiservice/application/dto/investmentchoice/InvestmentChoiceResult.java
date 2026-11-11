package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.Objects;

public record InvestmentChoiceResult(String accountNumber, InvestmentChoiceDetails details) {

  public InvestmentChoiceResult {
    Objects.requireNonNull(accountNumber, "accountNumber must not be null");
    Objects.requireNonNull(details, "details must not be null");
  }
}
