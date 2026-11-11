package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

public record InvestmentChoiceFund(
    String code,
    String name,
    String chineseName,
    Integer sequence,
    String riskLevel,
    boolean avail) {

  public InvestmentChoiceFund(
      String code, String name, String chineseName, String riskLevel, boolean avail) {
    this(code, name, chineseName, null, riskLevel, avail);
  }
}
