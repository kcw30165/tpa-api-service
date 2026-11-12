package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

public record InvestmentChoiceAllocationResponse(
    String switchSourceCode,
    String switchSource,
    String fundCode,
    String fundName,
    String percentage) {}
