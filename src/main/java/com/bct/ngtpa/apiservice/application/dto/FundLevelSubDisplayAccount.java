package com.bct.ngtpa.apiservice.application.dto;

public record FundLevelSubDisplayAccount(
    String subDisplayAccountName,
    String subDisplayAccountId,
    String unitBalance,
    String accountBalance,
    String fundPrice,
    String fxRate) {}
