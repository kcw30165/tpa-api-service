package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

public record InvestmentChoiceSource(String code, String description, int seq, boolean avail) {

  public InvestmentChoiceSource(String code, int seq, boolean avail) {
    this(code, null, seq, avail);
  }
}
