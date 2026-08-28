package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

public record RawAccountBalanceDisplaySource(
    String displaySource,
    String sourceDescription,
    String sourceChineseDescription,
    String subDisplaySource,
    String subSourceDescription,
    String subSourceChineseDescription,
    Integer sequence) {}
