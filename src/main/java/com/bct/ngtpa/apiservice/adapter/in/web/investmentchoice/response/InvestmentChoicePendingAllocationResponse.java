package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

public record InvestmentChoicePendingAllocationResponse(
    String switchSourceCode,
    String switchSource,
    String fundCode,
    String fundName,
    int percentage) {}
