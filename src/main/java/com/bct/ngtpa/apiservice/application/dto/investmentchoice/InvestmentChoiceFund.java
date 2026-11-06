package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

public record InvestmentChoiceFund(
    String code, String name, String chineseName, String riskLevel, boolean avail) {}
